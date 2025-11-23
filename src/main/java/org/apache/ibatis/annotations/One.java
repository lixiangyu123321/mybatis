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
package org.apache.ibatis.annotations;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import org.apache.ibatis.mapping.FetchType;

/**
 * **** 与Many类似，都是为了处理嵌套实体的关联查询操作 ****
 * 处理一对一（One-to-One）关联查询的核心注解
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface One {

  /**
   * 指定关联查询的Mapper语句ID
   * MyBatis 会将主查询的某个字段值作为参数传入该 Mapper 方法，执行查询并返回单个关联对象（如UserDetail）。
   * 关键约束：该 Mapper 方法必须接收一个与主查询关联字段类型匹配的参数（如用户 ID），且返回值必须为单个实体对象（而非集合），否则 MyBatis 会抛出类型转换异常。
   * @return
   */
  String select() default "";

  /**
   * 指定加载策略
   * @return
   */
  FetchType fetchType() default FetchType.DEFAULT;

}
