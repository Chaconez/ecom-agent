package com.ecom.cs.config;

import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.exception.NotRoleException;
import cn.dev33.satoken.exception.SaTokenException;
import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.router.SaRouter;
import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Sa-Token 全局配置：
 * 1. 路由鉴权拦截器（/api/auth/login 和 OPTIONS 预检请求放行）
 * 2. 通过 CorsFilter（Servlet Filter 级）处理 CORS，
 *    确保 OPTIONS 预检请求在触发 sa-token 鉴权之前就已放行
 *
 * <p>注意：注解校验（{@code @SaCheckLogin} / {@code @SaCheckRole}）由
 * {@link SaInterceptor#preHandle} 执行，抛出的异常不经过
 * {@code @RestControllerAdvice}（异常解析器只覆盖控制器方法内异常），
 * 因此在这里统一捕获并直接写 JSON 响应，保证 401/403 语义对前端可见。</p>
 *
 * @author Hollis
 */
@Configuration
public class SaTokenConfigure implements WebMvcConfigurer {

    /** CORS 预检请求缓存时间（秒） */
    private static final long CORS_MAX_AGE_SECONDS = 3600L;

    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    /**
     * 使用 CorsFilter（Servlet Filter 级别）处理 CORS。
     * CorsFilter 在 Spring MVC 拦截器之前运行，确保浏览器 OPTIONS 预检请求
     * 不会触达 sa-token 鉴权逻辑，直接返回 200 + CORS 响应头。
     */
    @Bean
    public CorsFilter corsFilter() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOriginPatterns(List.of("*"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setExposedHeaders(List.of("Authorization"));
        config.setAllowCredentials(false);
        config.setMaxAge(CORS_MAX_AGE_SECONDS);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return new CorsFilter(source);
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> {
            SaRouter.match("/**")
                    .notMatch("/api/auth/login")
                    .check(r -> StpUtil.checkLogin());
        }) {
            @Override
            public boolean preHandle(HttpServletRequest request,
                                     HttpServletResponse response,
                                     Object handler) throws Exception {
                // OPTIONS 预检请求无 Token，直接放行（CorsFilter 已处理完毕）
                if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
                    return true;
                }
                try {
                    return super.preHandle(request, response, handler);
                } catch (NotRoleException e) {
                    // @SaCheckRole 校验失败（注意：sa-token 1.39 中 NotRoleException
                    // 直接继承 SaTokenException，必须排在 SaTokenException 之前捕获）
                    writeAuthError(response, HttpStatus.FORBIDDEN, "无权限访问该资源：需要管理员权限");
                    return false;
                } catch (NotPermissionException e) {
                    // @SaCheckPermission 校验失败
                    writeAuthError(response, HttpStatus.FORBIDDEN, "无权限访问该资源");
                    return false;
                } catch (SaTokenException e) {
                    // 含 NotLoginException：未登录 / token 无效或过期
                    writeAuthError(response, HttpStatus.UNAUTHORIZED, "未登录或登录已过期，请重新登录");
                    return false;
                }
            }

        }).addPathPatterns("/**");
    }

    /**
     * 直接向响应写入鉴权失败 JSON（拦截器层异常不走 @RestControllerAdvice，
     * 只能在此手动写响应；格式与 GlobalExceptionHandler 保持一致）。
     */
    private void writeAuthError(HttpServletResponse response, HttpStatus status, String message)
            throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", status.value());
        body.put("message", message);
        response.getWriter().write(JSON_MAPPER.writeValueAsString(body));
    }
}
