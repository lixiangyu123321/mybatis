/*
 *    Copyright 2009-2011 the original author or authors.
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
package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.cache.decorators.LruCache;
import org.apache.ibatis.cache.impl.PerpetualCache;

/**
 * **** 标注在Mapper接口上，为该Mapper接口的所有SQL操作配置二级缓存 ****
 * **** 二级缓存的命名空间，起的挺好的，这个名字，就是二级缓存生效的Mapper接口 ****
 * 是 MyBatis 中配置二级缓存命名空间的核心注解，用于替代 XML 映射文件中的 <cache> 标签，
 * 为单个 Mapper 接口声明专属的二级缓存配置。
 * 为类/接口注解
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface CacheNamespace {

  /**
   * 指定基础缓存的实现类，负责缓存的核心存储逻辑
   * 默认的时Mybatis最基础的缓存实现，基于HashMap存储数据，无自动淘汰/过期极致
   * 需要配合eviction属性的淘汰策略使用（如基于 Redis、Ehcache 实现分布式缓存）
   * 如果需要自己定义缓存可实现org.apache.ibatis.cache.Cache接口并指定此类。
   * @return
   */
  Class<? extends org.apache.ibatis.cache.Cache> implementation() default PerpetualCache.class;

  /**
   * 指定缓存的淘汰策略（装饰器类）
   * MyBatis 内置 4 种淘汰策略：
   * 1. LruCache（默认）：最近最少使用，淘汰最久未被访问的缓存项；
   * 2. FifoCache：先进先出，按缓存插入顺序淘汰；
   * 3. SoftCache：基于软引用（SoftReference），JVM 内存不足时淘汰；
   * 4. WeakCache：基于弱引用（WeakReference），GC 时直接淘汰。
   * @return
   */
  Class<? extends org.apache.ibatis.cache.Cache> eviction() default LruCache.class;

  /**
   * 指定缓存的自动刷新时间间隔
   * 默认值0表示不开启自动刷新，缓存仅在执行增删改操作时手动刷新；若设置为非 0 值（如60000），则缓存会每隔指定毫秒自动清空所有数据。
   * @return
   */
  long flushInterval() default 0;

  /**
   * 指定缓存的最大容量，即缓存中可存储的键值对数量上限
   * @return
   */
  int size() default 1024;

  /**
   * 指定缓存的读写模式，决定缓存的对象时可修改的还是可读的
   * - true（默认）：读写缓存，缓存的是对象的序列化副本，每次获取缓存返回对象的拷贝，避免多个线程修改同一对象导致数据不一致，性能略低但安全；
   * - false：只读缓存，缓存的是对象的引用，返回的是同一个对象实例，性能更高，但禁止修改缓存对象（否则会导致缓存数据污染）。
   * @return
   */
  boolean readWrite() default true;

  /**
   * 指定开启缓存的阻塞机制
   * 默认值false表示关闭；设置为true时，MyBatis 会使用BlockingCache装饰基础缓存，
   * 当缓存项不存在时，会阻塞后续获取该缓存项的请求，直到第一个请求加载数据并放入缓存，解决缓存击穿问题（适用于高并发场景下的热点数据缓存）。
   * * @return
   */
  boolean blocking() default false;
  
}
