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

import java.io.Serializable;
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

/**
 * @author Clinton Begin
 */
/**
 * CacheKey 是 MyBatis 生成缓存唯一标识的核心类，它通过组合 SQL 语句、参数、环境等多个因素，
 * 计算出一个唯一的哈希值（hashcode）和校验和（checksum），同时保留原始参数列表，既保证缓存键的唯一性，
 * 又能在哈希冲突时通过全量对比确保准确性。
 *
 * 缓存key
 * 一般缓存框架的数据结构基本上都是 Key-Value 方式存储，
 * MyBatis 对于其 Key 的生成采取规则为：[mappedStementId + offset + limit + SQL + queryParams + environment]生成一个哈希码
 *
 * MyBatis 生成 CacheKey 的核心因素包括：
 * MappedStatement ID（对应 Mapper 中的方法）；
 * SQL 语句；
 * SQL 参数（如 id=1）；
 * 环境（如数据源 ID）；
 * 分页参数（如 limit 10）；
 *
 * 流程：
 * 执行 SELECT * FROM user WHERE id = ? →
 * MyBatis 收集上述5个因素，封装为数组 →
 * new CacheKey(数组) → 生成唯一 CacheKey →
 * 作为缓存键存入 Cache（key=CacheKey，value=查询结果）→
 * 下次执行相同查询时，生成相同的 CacheKey →
 * 从 Cache 中获取缓存值，无需执行 SQL。
 */
public class CacheKey implements Cloneable, Serializable {

  private static final long serialVersionUID = 1146682552656046210L;

  /**
   * 空缓存键常量（表示无缓存键）
   */
  public static final CacheKey NULL_CACHE_KEY = new NullCacheKey();

  /**
   * 哈希计算的乘数（固定值，用于哈希算法），默认的multiplier
   */
  private static final int DEFAULT_MULTIPLYER = 37;
  /**
   * 哈希计算的初始值，也就是默认的hashcode
   */
  private static final int DEFAULT_HASHCODE = 17;

  /**
   * 哈希计算乘数
   */
  private int multiplier;
  /**
   * 最终生成的哈希值
   */
  private int hashcode;
  /**
   * 校验和
   */
  private long checksum;
  /**
   * 参数计数（记录参与计算的参数个数）
   */
  private int count;
  /**
   * 原始参数列表
   */
  private List<Object> updateList;

  public CacheKey() {
    this.hashcode = DEFAULT_HASHCODE;
    this.multiplier = DEFAULT_MULTIPLYER;
    this.count = 0;
    this.updateList = new ArrayList<Object>();
  }

  public CacheKey(Object[] objects) {
    this();
    updateAll(objects);
  }

  public int getUpdateCount() {
    return updateList.size();
  }

  /**
   * 单参数更新
   * @param object
   */
  public void update(Object object) {
    if (object != null && object.getClass().isArray()) {
      int length = Array.getLength(object);
      for (int i = 0; i < length; i++) {
        Object element = Array.get(object, i);
        doUpdate(element);
      }
    } else {
      doUpdate(object);
    }
  }

  /**
   * 基于单个参数信息计算hashcode
   * @param object
   */
  private void doUpdate(Object object) {
    // 获取参数的基础哈希值（null则为1）
    int baseHashCode = object == null ? 1 : object.hashCode();

    // 更新计数、校验和
    count++;
    checksum += baseHashCode;
    baseHashCode *= count;

    // 计算最终hashcode
    hashcode = multiplier * hashcode + baseHashCode;

    // 保存原始参数（哈希冲突时兜底）
    updateList.add(object);
  }

  /**
   * 批量更新参数信息
   * @param objects
   */
  public void updateAll(Object[] objects) {
    for (Object o : objects) {
      update(o);
    }
  }

  /**
   * 缓存键的对比
   * @param object
   * @return
   */
  @Override
  public boolean equals(Object object) {
    if (this == object) {
      return true;
    }
    if (!(object instanceof CacheKey)) {
      return false;
    }

    final CacheKey cacheKey = (CacheKey) object;

    //先比hashcode，checksum，count，快速排除不相等的情况
    if (hashcode != cacheKey.hashcode) {
      return false;
    }
    if (checksum != cacheKey.checksum) {
      return false;
    }
    if (count != cacheKey.count) {
      return false;
    }

    //万一两个CacheKey的hash码碰巧一样，再根据参数列表来对比
    for (int i = 0; i < updateList.size(); i++) {
      Object thisObject = updateList.get(i);
      Object thatObject = cacheKey.updateList.get(i);
      if (thisObject == null) {
        if (thatObject != null) {
          return false;
        }
      } else {
        if (!thisObject.equals(thatObject)) {
          return false;
        }
      }
    }
    return true;
  }

  @Override
  public int hashCode() {
    return hashcode;
  }

  @Override
  public String toString() {
    StringBuilder returnValue = new StringBuilder().append(hashcode).append(':').append(checksum);
    for (int i = 0; i < updateList.size(); i++) {
      returnValue.append(':').append(updateList.get(i));
    }

    return returnValue.toString();
  }

  @Override
  public CacheKey clone() throws CloneNotSupportedException {
    CacheKey clonedCacheKey = (CacheKey) super.clone();
    clonedCacheKey.updateList = new ArrayList<Object>(updateList);
    return clonedCacheKey;
  }

}
