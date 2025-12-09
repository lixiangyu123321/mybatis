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
package org.apache.ibatis.exceptions;

/**
 * @author Clinton Begin
 */
/**
 * 
 * 是 MyBatis 中专门用于表示「查询结果数量超出预期」的异常类，它继承自 MyBatis 核心的PersistenceException（持久层异常），
 * 是 MyBatis 异常体系中分工明确的 “专用异常”。
 *
 * 核心使用场景
 * // MyBatis的DefaultSqlSession.selectOne方法简化逻辑
 * public <T> T selectOne(String statement, Object parameter) {
 *   // 执行查询，获取结果列表
 *   List<T> list = this.selectList(statement, parameter);
 *   // 判断结果数：0条返回null，1条返回第一条，多条抛出异常
 *   if (list.size() == 1) {
 *     return list.get(0);
 *   } else if (list.size() > 1) {
 *     // 抛出TooManyResultsException，带明确的语义提示
 *     throw new TooManyResultsException("Expected one result (or null) to be returned by selectOne(), but found: " + list.size());
 *   } else {
 *     return null;
 *   }
 * }
 */
public class TooManyResultsException extends PersistenceException {

  private static final long serialVersionUID = 8935197089745865786L;

  public TooManyResultsException() {
    super();
  }

  public TooManyResultsException(String message) {
    super(message);
  }

  public TooManyResultsException(String message, Throwable cause) {
    super(message, cause);
  }

  public TooManyResultsException(Throwable cause) {
    super(cause);
  }
}
