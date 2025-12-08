/*
 * Copyright 2012-2013 MyBatis.org.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.ibatis.builder;

import java.util.HashMap;

/**
 * 是 MyBatis 解析 SQL 参数表达式（如 #{id,jdbcType=INTEGER} 或 #{(user.id + 1),typeHandler=MyTypeHandler}）的核心工具类，
 * 它继承自 HashMap<String, String>，会将复杂的参数表达式拆解为键值对（如 property=id、jdbcType=INTEGER），为后续参数绑定提供标准化的配置。
 *
 *
 * 核心定位
 * MyBatis 中 SQL 参数的写法支持多种扩展配置，比如：
 * 基础写法：#{id} → 拆解为 property=id；
 * 带 JDBC 类型：#{id,jdbcType=INTEGER} → 拆解为 property=id、jdbcType=INTEGER；
 * 带表达式：#{(user.id + 1),typeHandler=MyTypeHandler} → 拆解为 expression=(user.id + 1)、typeHandler=MyTypeHandler；
 * 多配置：#{name,jdbcType=VARCHAR,mode=IN,size=50} → 拆解为多个键值对；
 *
 * ParameterExpression 的核心就是把这些 “逗号分隔、键值对形式” 的表达式，解析成 Map 中的键值对，
 * 让 MyBatis 后续能快速获取参数的各项配置。
 *
 * 核心流程：
 * 构造方法传入表达式 → parse 方法入口 → 区分“表达式型”(带())和“属性型”(普通) →
 * 解析核心配置（property/expression） → 解析扩展配置（jdbcType/typeHandler等） →
 * 所有配置存入Map
 *
 * 参数核心部分（属性 / 表达式）与扩展配置（如 jdbcType）之间，既支持逗号（,）分隔，也支持冒号（:）分隔（仅针对 jdbcType 简写）
 */
public class ParameterExpression extends HashMap<String, String> {

  private static final long serialVersionUID = -2417552199605158680L;

  public ParameterExpression(String expression) {
    parse(expression);
  }

  /**
   * 解析方法入口
   * @param expression
   */
  private void parse(String expression) {
    // 步骤1：跳过表达式开头的空白字符（如空格、制表符），返回第一个非空白字符的位置
    int p = skipWS(expression, 0);
    // 步骤2：判断是否是“表达式型”参数（开头是(）
    if (expression.charAt(p) == '(') {
      // 解析带()的表达式（如(user.id + 1)）
      expression(expression, p + 1);
    } else {
      // 解析普通属性（如id）
      property(expression, p);
    }
  }

  private void expression(String expression, int left) {
    int match = 1;
    int right = left + 1;
    while (match > 0) {
      if (expression.charAt(right) == ')') {
        match--;
      } else if (expression.charAt(right) == '(') {
        // 遇到(，未闭合数加1（处理嵌套括号）
        match++;
      }
      right++;
    }
    put("expression", expression.substring(left, right - 1));
    // 解析括号后的扩展配置，如（如,jdbcType=INTEGER）
    jdbcTypeOpt(expression, right);
  }

  /**
   * 解析普通属性，并存入map中
   * @param expression
   * @param left
   */
  private void property(String expression, int left) {
    if (left < expression.length()) {
      int right = skipUntil(expression, left, ",:");
      put("property", trimmedStr(expression, left, right));
      jdbcTypeOpt(expression, right);
    }
  }

  /**
   * 跳过开头空白
   * @param expression
   * @param p
   * @return
   */
  private int skipWS(String expression, int p) {
    for (int i = p; i < expression.length(); i++) {
      if (expression.charAt(i) > 0x20) {
        return i;
      }
    }
    return expression.length();
  }

  private int skipUntil(String expression, int p, final String endChars) {
    for (int i = p; i < expression.length(); i++) {
      char c = expression.charAt(i);
      if (endChars.indexOf(c) > -1) {
        return i;
      }
    }
    return expression.length();
  }

  private void jdbcTypeOpt(String expression, int p) {
    p = skipWS(expression, p);
    if (p < expression.length()) {
      if (expression.charAt(p) == ':') {
        jdbcType(expression, p + 1);
      } else if (expression.charAt(p) == ',') {
        option(expression, p + 1);
      } else {
        throw new BuilderException("Parsing error in {" + new String(expression) + "} in position " + p);
      }
    }
  }

  private void jdbcType(String expression, int p) {
    int left = skipWS(expression, p);
    // 找到下一个逗号的位置
    int right = skipUntil(expression, left, ",");
    if (right > left) {
      // 提起JDBC类型，存入jdbcType键中
      put("jdbcType", trimmedStr(expression, left, right));
    } else {
      throw new BuilderException("Parsing error in {" + new String(expression) + "} in position " + p);
    }
    option(expression, right + 1);
  }

  /**
   * 解析扩展配置
   * @param expression
   * @param p
   */
  private void option(String expression, int p) {
    int left = skipWS(expression, p);
    if (left < expression.length()) {
      int right = skipUntil(expression, left, "=");
      String name = trimmedStr(expression, left, right);
      left = right + 1;
      right = skipUntil(expression, left, ",");
      String value = trimmedStr(expression, left, right);
      put(name, value);
      option(expression, right + 1);
    }
  }

  /**
   * 修建字符串，跳过空串以及跳过首尾空白字符串
   * @param str
   * @param start
   * @param end
   * @return
   */
  private String trimmedStr(String str, int start, int end) {
    while (str.charAt(start) <= 0x20) {
      start++;
    }
    while (str.charAt(end - 1) <= 0x20) {
      end--;
    }
    return start >= end ? "" : str.substring(start, end);
  }

}
