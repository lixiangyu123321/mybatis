package org.apache.ibatis.cache.decorators;
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

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.cache.CacheException;

/**
 * BlockingCache 是 MyBatis 提供的一种 “阻塞式缓存” 装饰器，
 * 它为缓存的每个键（Key）绑定一个可重入锁（ReentrantLock），
 * 保证同一时刻只有一个线程能加载某个键的缓存数据，解决了高并发场景下的 “缓存击穿”（Cache Stampede）问题。
 */
public class BlockingCache implements Cache {

  /**
   * 锁超时时间，ReentrantLock设置超时时间
   */
  private long timeout;
  /**
   * 装饰器模式：持有并实现接口
   */
  private final Cache delegate;
  /**
   * 按key存储的锁映射表，每一个缓存有对应的一个ReentrantLock
   */
  private final ConcurrentHashMap<Object, ReentrantLock> locks;

  public BlockingCache(Cache delegate) {
    this.delegate = delegate;
    this.locks = new ConcurrentHashMap<Object, ReentrantLock>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  /**
   * 使用 finally 块保证锁一定会释放，避免死锁；
   * 只有当第一个线程加载完数据并写入缓存后，才释放锁，其他线程此时读取缓存就能命中，无需穿透到数据库
   */
  @Override
  public void putObject(Object key, Object value) {
    try {
      delegate.putObject(key, value);
    } finally {
      releaseLock(key);
    }
  }

  @Override
  public Object getObject(Object key) {
    // 阻塞/超时获得当前key的锁
    acquireLock(key);
    Object value = delegate.getObject(key);
    if (value != null) {
      releaseLock(key);
    }
    // 如果缓存为空，则只有一个线程回去数据库中查询到数据
    return value;
  }

  @Override
  public Object removeObject(Object key) {
    return delegate.removeObject(key);
  }

  @Override
  public void clear() {
    delegate.clear();
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }
  
  private ReentrantLock getLockForKey(Object key) {
    ReentrantLock lock = new ReentrantLock();
    // 原子操作放入Map（putIfAbsent保证同一Key只有一个锁）
    // 等于判断有没有，放入整合成了一个原子操作
    ReentrantLock previous = locks.putIfAbsent(key, lock);
    return previous == null ? lock : previous;
  }
  
  private void acquireLock(Object key) {
    Lock lock = getLockForKey(key);
    if (timeout > 0) {
      try {
        boolean acquired = lock.tryLock(timeout, TimeUnit.MILLISECONDS);
        if (!acquired) {
          throw new CacheException("Couldn't get a lock in " + timeout + " for the key " +  key + " at the cache " + delegate.getId());  
        }
      } catch (InterruptedException e) {
        throw new CacheException("Got interrupted while trying to acquire lock for key " + key, e);
      }
    } else {
      lock.lock();
    }
  }
  
  private void releaseLock(Object key) {
    ReentrantLock lock = locks.get(key);
    /**
     * 这里可能只是putObject，并不会获得锁，也就无需释放
     * 判断当前线程是否持有锁，如果持有，则释放
     */
    if (lock.isHeldByCurrentThread()) {
      lock.unlock();
    }
  }

  public long getTimeout() {
    return timeout;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }  
}