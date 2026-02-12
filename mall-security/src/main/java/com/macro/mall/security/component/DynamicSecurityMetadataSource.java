package com.macro.mall.security.component;

import cn.hutool.core.util.URLUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.ConfigAttribute;
import org.springframework.security.web.FilterInvocation;
import org.springframework.security.web.access.intercept.FilterInvocationSecurityMetadataSource;
import org.springframework.util.AntPathMatcher;
import org.springframework.util.PathMatcher;

import javax.annotation.PostConstruct;
import java.util.*;

/**
 * 动态权限数据源，用于获取动态权限规则
 * Created by macro on 2020/2/7.
 */
public class DynamicSecurityMetadataSource implements FilterInvocationSecurityMetadataSource {

    /**
     * 假设性修复：
     * 在高并发下该缓存可能被并发初始化/清理，导致偶发 NPE 或并发读写问题。
     * 这里使用 volatile + 局部快照 + 同步初始化来做防御性增强，不改变原有权限匹配逻辑。
     */
    private static volatile Map<String, ConfigAttribute> configAttributeMap = null;
    @Autowired
    private DynamicSecurityService dynamicSecurityService;

    @PostConstruct
    public void loadDataSource() {
        configAttributeMap = dynamicSecurityService.loadDataSource();
    }

    public void clearDataSource() {
        // 防御性：并发场景下可能为 null，且不直接 clear 避免影响正在读取的线程
        configAttributeMap = null;
    }

    @Override
    public Collection<ConfigAttribute> getAttributes(Object o) throws IllegalArgumentException {
        Map<String, ConfigAttribute> localMap = configAttributeMap;
        if (localMap == null) {
            synchronized (this) {
                if (configAttributeMap == null) {
                    this.loadDataSource();
                }
                localMap = configAttributeMap;
            }
        }
        List<ConfigAttribute>  configAttributes = new ArrayList<>();
        //获取当前访问的路径
        String url = ((FilterInvocation) o).getRequestUrl();
        String path = URLUtil.getPath(url);
        PathMatcher pathMatcher = new AntPathMatcher();
        if (localMap == null || localMap.isEmpty()) {
            // 未设置操作请求权限，返回空集合
            return configAttributes;
        }
        Iterator<String> iterator = localMap.keySet().iterator();
        //获取访问该路径所需资源
        while (iterator.hasNext()) {
            String pattern = iterator.next();
            if (pathMatcher.match(pattern, path)) {
                configAttributes.add(localMap.get(pattern));
            }
        }
        // 未设置操作请求权限，返回空集合
        return configAttributes;
    }

    @Override
    public Collection<ConfigAttribute> getAllConfigAttributes() {
        return null;
    }

    @Override
    public boolean supports(Class<?> aClass) {
        return true;
    }

}
