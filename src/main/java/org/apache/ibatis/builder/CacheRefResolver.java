/*
 *    Copyright 2009-2012 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.builder;

import org.apache.ibatis.cache.Cache;

/**
 * @author Clinton Begin
 */
/**
 * 是 MyBatis 中处理 Mapper 接口间缓存引用（<cache-ref>）的解析器类
 *
 */
public class CacheRefResolver {
  /**
   * 是 MyBatis 解析 Mapper 配置时的核心辅助类，封装了缓存创建、缓存引用绑定、SQL 语句构建等通用逻辑。
   * CacheRefResolver不直接处理缓存引用的底层逻辑，而是委托该助手类的useCacheRef方法完成实际解析。
   */
  private final MapperBuilderAssistant assistant;
  /**
   * 缓存引用的目标命名空间
   * 确定共享的目标的缓存命名空间
   */
  private final String cacheRefNamespace;

  public CacheRefResolver(MapperBuilderAssistant assistant, String cacheRefNamespace) {
    this.assistant = assistant;
    this.cacheRefNamespace = cacheRefNamespace;
  }

  /**
   * 出发缓存引用的解析流程，返回目标Mapper对应的二级缓存
   * @return
   */
  public Cache resolveCacheRef() {
    return assistant.useCacheRef(cacheRefNamespace);
  }
}