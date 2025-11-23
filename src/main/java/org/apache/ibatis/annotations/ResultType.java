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
package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This annotation can be used when a @Select method is using a
 * ResultHandler.  Those methods must have void return type, so
 * this annotation can be used to tell MyBatis what kind of object
 * it should build for each row.
 *
 * 显式指定查询结果封装类型的核心注解。它的核心作用是为 Mapper 接口的查询方法指定结果集的目标封装类型
 * @since 3.2.0
 * @author Jeff Butler
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ResultType {

  /**
   * 指定查询结果的目标封装类型
   * 支持的类型包括
   * 1. 实体类（如User.class）：结果集映射为实体对象 / 实体集合；
   * 2. 基本类型 / 包装类型（如Integer.class/String.class）：结果集映射为单个基本类型值 / 基本类型集合；
   * 3. Map.class：结果集映射为Map<String, Object>（键为列名，值为列值）/List<Map<String, Object>>。
   *
   * 若resultType为实体类：MyBatis 通过自动映射（驼峰转换 / 列名与属性名一致）将ResultSet数据封装为实体对象 / 实体集合；
   * 若resultType为基本类型 / 包装类型：提取ResultSet的单个列值，封装为基本类型值 / 基本类型集合；
   * 若resultType为Map.class：将ResultSet的每一行转换为Map<String, Object>（键为列名，值为列值），最终封装为Map/List<Map>；
   *
   * **** 返回对象为实体类，则是由列名找属性去映射 ****
   * **** 返回对象为基本类型，如果查询多列，只返回第一列的数据
   * @return
   */
  Class<?> value();
}
