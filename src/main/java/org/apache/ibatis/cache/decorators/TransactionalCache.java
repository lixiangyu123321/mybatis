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
 *
 * 事务执行 + 提交
 * ┌───────────┐    ┌──────────────────┐    ┌───────────┐    ┌─────┐
 * │ SqlSession│    │ TransactionalCache│    │ delegate  │    │ DB  │
 * └─────┬─────┘    └──────────┬───────┘    └────┬──────┘    └──┬──┘
 *       │                     │                   │             │
 *       │ 1. 开启事务          │                   │             │
 *       │────────────────────>│                   │             │
 *       │                     │                   │             │
 *       │ 2. 查询缓存(key=1)  │                   │             │
 *       │────────────────────>│                   │             │
 *       │                     │ 3. 调用getObject(1)│             │
 *       │                     │──────────────────>│             │
 *       │                     │                   │             │
 *       │                     │ 4. 返回null（未命中）│             │
 *       │                     │<──────────────────│             │
 *       │                     │                   │             │
 *       │                     │ 5. 记录key=1到entriesMissedInCache │
 *       │                     │───[本地集合操作]───>             │
 *       │                     │                   │             │
 *       │ 6. 缓存未命中，查询DB │                   │             │
 *       │─────────────────────────────────────────────>│             │
 *       │                     │                   │             │
 *       │ 7. DB返回数据(user=1)│                   │             │
 *       │<─────────────────────────────────────────────│             │
 *       │                     │                   │             │
 *       │ 8. 写入缓存(key=1, value=user1)│             │             │
 *       │────────────────────>│                   │             │
 *       │                     │ 9. 暂存到entriesToAddOnCommit │
 *       │                     │───[本地Map操作]───>             │
 *       │                     │                   │             │
 *       │ 10. 执行事务提交     │                   │             │
 *       │────────────────────>│                   │             │
 *       │                     │ 11. 调用delegate.clear()(若clearOnCommit=true)│
 *       │                     │──────────────────>│             │
 *       │                     │ 12. 清空成功响应  │             │
 *       │                     │<──────────────────│             │
 *       │                     │                   │             │
 *       │                     │ 13. 遍历entriesToAddOnCommit，批量put到delegate │
 *       │                     │──────────────────>│             │
 *       │                     │ 14. 批量写入成功  │             │
 *       │                     │<──────────────────│             │
 *       │                     │                   │             │
 *       │                     │ 15. 遍历entriesMissedInCache，填充空值(未存在的key)│
 *       │                     │──────────────────>│             │
 *       │                     │ 16. 空值填充成功  │             │
 *       │                     │<──────────────────│             │
 *       │                     │                   │             │
 *       │                     │ 17. 调用reset()，清空本地集合/标记 │
 *       │                     │───[重置状态]──────>             │
 *       │                     │                   │             │
 *       │ 18. 事务提交成功响应 │                   │             │
 *       │<────────────────────│                   │             │
 * ┌─────┴─────┐    ┌──────────┴───────┐    ┌────┴──────┐    ┌──┴──┐
 * │ SqlSession│    │ TransactionalCache│    │ delegate  │    │ DB  │
 * └───────────┘    └──────────────────┘    └───────────┘    └─────┘
 *
 * 事务执行+回滚
 *┌───────────┐    ┌──────────────────┐    ┌───────────┐
 * │ SqlSession│    │ TransactionalCache│    │ delegate  │
 * └─────┬─────┘    └──────────┬───────┘    └────┬──────┘
 *       │                     │                   │
 *       │ ... 执行步骤1-9（同场景1）... │                   │
 *       │                     │                   │
 *       │ 10. 事务异常，执行回滚 │                   │
 *       │────────────────────>│                   │
 *       │                     │                   │
 *       │                     │ 11. 调用unlockMissedEntries() │
 *       │                     │───┐               │
 *       │                     │   │ 遍历entriesMissedInCache │
 *       │                     │   │ 为每个key调用delegate.put(key, null) │
 *       │                     │───┘──────────────>│
 *       │                     │ 12. 空值填充成功  │
 *       │                     │<──────────────────│
 *       │                     │                   │
 *       │                     │ 13. 调用reset()，清空本地集合/标记 │
 *       │                     │───[重置状态]──────>│
 *       │                     │                   │
 *       │ 14. 事务回滚成功响应 │                   │
 *       │<────────────────────│                   │
 * ┌─────┴─────┐    ┌──────────┴───────┐    ┌────┴──────┐
 * │ SqlSession│    │ TransactionalCache│    │ delegate  │
 * └───────────┘    └──────────────────┘    └───────────┘
 *
 * 事务内执行缓存清空 + 提交
 * ┌───────────┐    ┌──────────────────┐    ┌───────────┐
 * │ SqlSession│    │ TransactionalCache│    │ delegate  │
 * └─────┬─────┘    └──────────┬───────┘    └────┬──────┘
 *       │                     │                   │
 *       │ ... 开启事务 ...    │                   │
 *       │                     │                   │
 *       │ 调用cache.clear()   │                   │
 *       │────────────────────>│                   │
 *       │                     │ 1. 设置clearOnCommit=true │
 *       │                     │───[本地标记]──────>│
 *       │                     │ 2. 清空entriesToAddOnCommit │
 *       │                     │───[本地Map清空]───>│
 *       │                     │                   │
 *       │ ... 后续查询/写入 ...│                   │
 *       │                     │ （查询时因clearOnCommit=true，直接返回null） │
 *       │                     │                   │
 *       │ 执行事务提交        │                   │
 *       │────────────────────>│                   │
 *       │                     │ 3. 调用delegate.clear() │
 *       │                     │──────────────────>│
 *       │                     │ 4. 底层缓存清空成功 │
 *       │                     │<──────────────────│
 *       │                     │ 5. 后续flush/reset同场景1 │
 *       │                     │                   │
 * └─────┬─────┘    └──────────┬───────┘    └────┬──────┘
 *
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
  /**
   * 记录缓存未命中的key
   */
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

  /**
   * 缓存未命中的 key 记录到entriesMissedInCache：回滚时需要对这些 key 做 “空值填充”，避免后续查询再次穿透到数据库（MyBatis 的 “缓存防穿透” 设计）；
   * clearOnCommit为 true 时返回 null：如果事务中执行了clear()，则当前事务内的所有查询都返回 null，模拟 “缓存已清空” 的状态，直到 commit 生效。
   * @param key The key
   * @return
   */
  @Override
  public Object getObject(Object key) {
    // 先查底层缓存
    Object object = delegate.getObject(key);
    if (object == null) {
      // 缓存未命中（返回null），记录该key到missed集合
      entriesMissedInCache.add(key);
    }
    // 如果标记了“提交时清空缓存”，则返回null（模拟清空后的效果）
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

  /**
   * 事务期间的写入操作全部 “延迟执行”，只有 commit 时才会刷入底层缓存，保证事务内的缓存操作不会影响其他事务。
   */
  @Override
  public void putObject(Object key, Object object) {
    entriesToAddOnCommit.put(key, object);
  }

  /**
   * 事务性缓存中禁用了直接删除操作 ——MyBatis 的事务缓存只关注 “写入 / 清空”，删除操作需通过事务提交 / 回滚间接处理，避免数据不一致。
   * @param key The key
   * @return
   */
  @Override
  public Object removeObject(Object key) {
    return null;
  }

  /**
   * 不立即清空底层缓存，只设置clearOnCommit标记；
   * 同时清空本地暂存的写入操作，确保 commit 时不会有残留的写入。
   */
  @Override
  public void clear() {
    clearOnCommit = true;
    entriesToAddOnCommit.clear();
  }

  /**
   * 先清空（如果需要）：保证底层缓存的 “清空” 操作优先于写入，避免脏数据；
   * 再刷入待提交的写入：将事务期间的缓存写入批量同步到底层缓存；
   * 最后重置：为下一次事务做准备。
   */
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

  /**
   * 恢复初始状态
   */
  private void reset() {
    clearOnCommit = false;
    entriesToAddOnCommit.clear();
    entriesMissedInCache.clear();
  }

  private void flushPendingEntries() {
    // 批量写入待提交的key-value到底层缓存
    for (Map.Entry<Object, Object> entry : entriesToAddOnCommit.entrySet()) {
      delegate.putObject(entry.getKey(), entry.getValue());
    }
    // 为“缓存未命中”的key填充空值（防缓存穿透）
    for (Object entry : entriesMissedInCache) {
      if (!entriesToAddOnCommit.containsKey(entry)) {
        delegate.putObject(entry, null);
      }
    }
  }

  /**
   * 即使事务回滚，也要为 “未命中的 key” 填充空值，避免后续查询穿透到数据库。
   */
  private void unlockMissedEntries() {
    for (Object entry : entriesMissedInCache) {
      delegate.putObject(entry, null);
    }
  }

}
