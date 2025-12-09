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

import org.apache.ibatis.executor.ErrorContext;

/**
 * 统一封装持久化异常的工具类
 * 核心作用是将底层的原生异常包装成MyBatis自定义的PersistenceException。并通过ErrorContext补充异常上下文信息
 * 让异常更容易排查
 */
public class ExceptionFactory {

  private ExceptionFactory() {
  }

  /**
   * ExceptionFactory是一个静态工具类（无实例化能力），仅暴露一个静态方法wrapException：
   * 接收异常提示信息和原生异常；
   * 结合ErrorContext（MyBatis 的异常上下文）构建完整的异常描述；
   * 将原生异常包装为 MyBatis 统一的PersistenceException（运行时异常）并返回；
   * 核心目标：统一持久层异常类型，补充上下文信息，简化异常处理。
   * @param message
   * @param e
   * @return
   */
  public static RuntimeException wrapException(String message, Exception e) {
    return new PersistenceException(ErrorContext.instance().message(message).cause(e).toString(), e);
  }

}
