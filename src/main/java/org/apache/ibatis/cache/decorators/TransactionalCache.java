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

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.locks.ReadWriteLock;

import org.apache.ibatis.cache.Cache;

/**
 * TransactionalCache 是 MyBatis 提供的 “事务型缓存” 装饰器，
 * 它为底层缓存增加了 “事务提交 / 回滚” 能力 —— 缓存操作先暂存到本地，
 * 事务提交时才刷入底层缓存，回滚时放弃所有操作，核心价值是保证缓存数据与数据库事务的一致性。
 *
 * 核心思路：
 * 事务执行期间，所有缓存操作（put/clear）都暂存到本地集合，不直接修改底层缓存；
 * 事务提交时：将本地暂存的操作刷入底层缓存，保证缓存与数据库一致；
 * 事务回滚时：清空本地暂存的操作，放弃所有缓存修改，避免脏数据。
 */
public class TransactionalCache implements Cache {

  private Cache delegate;
  /**
   * 事务提交是否清空底层缓存
   */
  private boolean clearOnCommit;
  /**
   * 事务期间待提交的缓存写入操作，kv映射，commit时刷入delegate
   */
  private Map<Object, Object> entriesToAddOnCommit;
  private Set<Object> entriesMissedInCache;

  public TransactionalCache(Cache delegate) {
    this.delegate = delegate;
    //默认commit时不清缓存
    this.clearOnCommit = false;
    this.entriesToAddOnCommit = new HashMap<Object, Object>();
    this.entriesMissedInCache = new HashSet<Object>();
  }

  @Override
  public String getId() {
    return delegate.getId();
  }

  @Override
  public int getSize() {
    return delegate.getSize();
  }

  @Override
  public Object getObject(Object key) {
    // issue #116
    Object object = delegate.getObject(key);
    if (object == null) {
      entriesMissedInCache.add(key);
    }
    // issue #146
    if (clearOnCommit) {
      return null;
    } else {
      return object;
    }
  }

  @Override
  public ReadWriteLock getReadWriteLock() {
    return null;
  }

  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  @Override
  public Object removeObject(Object key) {
    return null;
  }

  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  //多了commit方法，提供事务功能
  public void commit() {
    if (clearOnCommit) {
      delegate.clear();
    }
    flushPendingEntries();
    reset();
  }

  public void rollback() {
    unlockMissedEntries();
    reset();
  }

  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      delegate.putObject(entry, null);
    }
  }

}
