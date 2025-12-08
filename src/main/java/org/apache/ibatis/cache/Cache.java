/*
 *    Copyright 2009-2014 the original author or authors.
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
package org.apache.ibatis.cache;

import java.util.concurrent.locks.ReadWriteLock;


/**
 * 是MyBatis定义的缓存核心接口
 * 它为所有缓存实现（如内存缓存、Redis 缓存）提供了统一的标准契约，
 * 包含缓存的增删改查、清空、大小统计等基础操作，部分方法为可选实现（核心流程不依赖）。
 */
public interface Cache {

  /**
   * @return 获取缓存唯一标识
   */
  String getId();

  /**
   * 存入缓存
   * @param key Can be any object but usually it is a {@link CacheKey}
   * @param value The result of a select.
   */
  void putObject(Object key, Object value);

  /**
   *  获得缓存值
   * @param key The key
   * @return The object stored in the cache.
   */
  Object getObject(Object key);

  /**
   * 删除缓存，可选
   * Optional. It is not called by the core.
   * 
   * @param key The key
   * @return The object that was removed
   */
  Object removeObject(Object key);

  /**
   * 清空缓存
   * Clears this cache instance
   */
  void clear();

  /**
   * 获得缓存数量
   * Optional. This method is not called by the core.
   * 
   * @return The number of elements stored in the cache (not its capacity).
   */
  int getSize();
  
  /**
   * 获取读写锁
   * 3.2.6 版本后 MyBatis 核心不再调用此方法；
   * 缓存的并发控制需由缓存实现内部自行处理（如 PerpetualCache 内部用 ReentrantReadWriteLock）；
   * Optional. As of 3.2.6 this method is no longer called by the core.
   *  
   * Any locking needed by the cache must be provided internally by the cache provider.
   * 
   * @return A ReadWriteLock 
   */
  ReadWriteLock getReadWriteLock();

}