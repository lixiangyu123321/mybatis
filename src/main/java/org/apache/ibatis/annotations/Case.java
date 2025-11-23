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
 * **** 就是基于数据库中某一个字段的值将某个记录映射为不同的Java实体类 ****
 * 是 MyBatis 中鉴别器（Discriminator）的核心配置注解，用于定义鉴别器的分支匹配规则，是 XML 映射文件中 <case> 标签的注解等价实现。
 * 鉴别器是 MyBatis 处理多态结果映射的核心机制：
 * 通过指定数据库列（鉴别列）的取值，动态选择不同的结果映射规则，
 * 将同一张表的不同行数据映射为不同的 Java 实体类（如将user_type=1的行映射为AdminUser，user_type=2的行映射为NormalUser）。
 * 而@Case正是配置「鉴别列取值」与「结果映射规则」的对应关系。
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Case {

  /**
   * 指定鉴别列的匹配值、
   * 当数据库中鉴别列的取值等于该字符串时，触发当前@Case的映射规则。
   * 注意：MyBatis 会将鉴别列的数据库值转为字符串后再匹配，因此数值型鉴别列（如int）的匹配值需写为字符串（如"1"）。
   * @return
   */
  String value();

  /**
   * 当前分支匹配成功后，结果集要映射的目标Java实体类
   * 例如鉴别列user_type值为"1"时，映射为AdminUser.class，值为"2"时映射为NormalUser.class。
   * @return
   */
  Class<?> type();

  /**
   * 指定基于Setter注入的结果映射规则
   * 与@Result注解配合，定义数据库列与目标实体类属性的映射关系（主流方式）。
   * 若该属性为空，会尝试使用constructArgs的构造器注入，或 MyBatis 自动映射规则。
   * @return
   */
  Result[] results() default {};

  /**
   * 指定基于构造器注入的结果映射规则， 与results互斥，通常只需要配置一个
   * @return
   */
  Arg[] constructArgs() default {};
}
