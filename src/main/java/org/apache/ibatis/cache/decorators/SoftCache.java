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
package org.apache.ibatis.cache.decorators;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.SoftReference;
import java.util.Deque;
import java.util.LinkedList;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * SoftCache 是 MyBatis 提供的 “软引用缓存” 装饰器，
 * 它基于 Java 的软引用（SoftReference）实现缓存管理 —— 内存充足时缓存正常生效，
 * 内存不足时 JVM 自动回收缓存数据，核心价值是避免缓存占用过多内存导致 OOM，
 * 同时通过硬引用链表保留高频访问数据，平衡缓存命中率和内存使用率。
 */
public class SoftCache implements Cache {
  /**
   * 硬引用链表：存储高频访问的缓存值，防止被GC回收（默认容量256）
   */
  private final Deque<Object> hardLinksToAvoidGarbageCollection;
  /**
   * 引用队列：JVM回收软引用后，会将SoftEntry加入此队列，用于清理失效缓存
   */
  private final ReferenceQueue<Object> queueOfGarbageCollectedEntries;
  private final Cache delegate;
  /**
   *  硬引用链表的最大容量（默认256）
   */
  private int numberOfHardLinks;

  public SoftCache(Cache delegate) {
    this.delegate = delegate;
    //默认链表可以存256元素
    this.numberOfHardLinks = 256;
    this.hardLinksToAvoidGarbageCollection = new LinkedList<Object>();
    this.queueOfGarbageCollectedEntries = new ReferenceQueue<Object>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    removeGarbageCollectedItems();
    return delegate.getSize();
  }


  public void setSize(int size) {
    this.numberOfHardLinks = size;
  }

  @Override
  public void putObject(Object key, Object value) {
    removeGarbageCollectedItems();
    //putObject存了一个SoftReference，这样value没用时会自动垃圾回收
    delegate.putObject(key, new SoftEntry(key, value, queueOfGarbageCollectedEntries));
  }

  @Override
  public Object getObject(Object key) {
    Object result = null;
    @SuppressWarnings("unchecked") // assumed delegate cache is totally managed by this cache
    SoftReference<Object> softReference = (SoftReference<Object>) delegate.getObject(key);
    if (softReference != null) {
        //核心调用SoftReference.get取得元素
      result = softReference.get();
      if (result == null) {
        delegate.removeObject(key);
      } else {
        // See #586 (and #335) modifications need more than a read lock 
        synchronized (hardLinksToAvoidGarbageCollection) {
            //存入经常访问的键值到链表(最多256元素),防止垃圾回收
          hardLinksToAvoidGarbageCollection.addFirst(result);
          if (hardLinksToAvoidGarbageCollection.size() > numberOfHardLinks) {
            hardLinksToAvoidGarbageCollection.removeLast();
          }
        }
      }
    }
    return result;
  }

  @Override
  public Object removeObject(Object key) {
    removeGarbageCollectedItems();
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    synchronized (hardLinksToAvoidGarbageCollection) {
      hardLinksToAvoidGarbageCollection.clear();
    }
    removeGarbageCollectedItems();
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  private void removeGarbageCollectedItems() {
    SoftEntry sv;
    // 从软引用队列中主键清楚
    while ((sv = (SoftEntry) queueOfGarbageCollectedEntries.poll()) != null) {
      delegate.removeObject(sv.key);
    }
  }

  /**
   * 软引用包装类
   *
   * 内存结构：
   * ┌─────────────────────────────────────────────────────┐
   * │                   HashMap (cacheMap)                 │
   * ├─────────────────┬───────────────────────────────────┤
   * │ 键 (强引用)      │ 值 (强引用)                       │
   * │ "user:1001" ──→ │ SoftEntry实例                     │
   * │                 │   ┌─────────────────────────────┐ │
   * │ "user:1002" ──→ │   │ SoftEntry实例               │ │
   * └─────────────────┴───┼─────────────────────────────┤ │
   *                       │ - key: "user:1001" (强引用) │ │
   *                       │ - value: User对象 (软引用)  │ │
   *                       └─────────────────────────────┘ │
   */
  private static class SoftEntry extends SoftReference<Object> {
    private final Object key;

    SoftEntry(Object key, Object value, ReferenceQueue<Object> garbageCollectionQueue) {
      super(value, garbageCollectionQueue);
      this.key = key;
    }
  }

}