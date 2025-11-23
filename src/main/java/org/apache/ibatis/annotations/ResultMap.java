/*
 *    Copyright 2009-2013 the original author or authors.
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

/**
 * 引用已定义结果映射（ResultMap）的核心注解
 * 实现结果映射规则的复用
 * 当多个 Mapper 方法需要使用相同的列 - 属性映射、关联查询规则时，无需重复编写@Results注解，
 * 只需通过@ResultMap引用已定义的 ResultMap 唯一标识（ID）即可。
 * @author Jeff Butler
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ResultMap {

  /**
   * 指定已定义 ResultMap 的唯一标识（ID）
   * 1. ID 必须与已定义的 ResultMap（XML 式 / 注解式）的 ID 完全一致；
   * 2. 若引用其他命名空间的 ResultMap，需添加命名空间前缀（如com.mapper.UserMapper.UserResultMap）；
   * 3. 数组形式支持指定多个 ResultMap ID，但 MyBatis 仅会使用第一个有效 ID（多 ID 为兼容设计，实际极少使用）。
   * @return
   */
  String[] value();
}
