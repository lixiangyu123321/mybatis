/*
 *    Copyright 2012 the original author or authors.
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
 * LanguageDriver 主要负责解析xml/注解字符串中的SQL脚本并解析为SqlSource，最终生成可执行的BoundSql对象
 * 为 Mapper 接口的单个方法（或全局 / 类级别）指定自定义的 SQL 解析器，替代 MyBatis 默认的 SQL 解析逻辑，
 * 实现对 SQL 语句的个性化解析、生成或处理（如支持模板引擎生成 SQL、自定义动态 SQL 语法等）。
 * MyBatis 中指定 SQL 语言驱动
 * @author Clinton Begin
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Lang {

  /**
   * 只当语言驱动的实现类
   * 该类必须实现 MyBatis 核心接口org.apache.ibatis.scripting.LanguageDriver（MyBatis 语言驱动的标准接口）。
   * MyBatis 会通过反射创建该类的实例，并用其替代默认驱动，完成对当前 Mapper 方法的 SQL 解析、参数绑定和BoundSql对象的构建。
   * @return
   */
  Class<?> value();
}
