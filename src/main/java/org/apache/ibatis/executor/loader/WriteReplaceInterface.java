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
package org.apache.ibatis.executor.loader;

import java.io.ObjectStreamException;

/**
 * 是Java序列化体系的一个特殊的标记接口，
 * 核心作用是：当一个对象要被序列化（比如存到文件/Redis），用另一个对象“替换”它去序列化
 *
 * 场景背景
 * MyBatis 的懒加载代理对象（比如 User 的 order 属性是代理对象）不能直接序列化：
 * 代理对象里包含ResultLoader、Executor等不能序列化的对象（比如数据库连接）；
 * 如果直接把 User 序列化存到 Redis，反序列化时会报错（因为代理对象的依赖都没了）。
 * 解决方案：用WriteReplaceInterface替换代理对象
 * MyBatis 的懒加载代理对象（比如 Order 的代理）实现WriteReplaceInterface；
 * 序列化代理对象时，触发writeReplace()，返回 “真实的、已加载的 Order 对象”（如果没加载，就先触发加载）；
 * 序列化的是真实 Order 对象（没有代理的冗余信息），反序列化后正常使用。
 * 代码例子（简化版）
 * // 懒加载代理对象（MyBatis自动生成）
 * public class OrderProxy extends Order implements WriteReplaceInterface, Serializable {
 *     // 懒加载相关的非序列化属性
 *     private transient ResultLoader resultLoader;
 *
 *     @Override
 *     public Object writeReplace() throws ObjectStreamException {
 *         // 序列化前：先触发懒加载，获取真实的Order对象
 *         if (resultLoader != null && !resultLoader.loaded) {
 *             try {
 *                 resultLoader.loadResult(); // 查数据库，加载真实Order
 *             } catch (SQLException e) {
 *                 throw new ObjectStreamException("懒加载失败") {};
 *             }
 *         }
 *         // 返回真实的Order对象（代替代理对象序列化）
 *         Order realOrder = new Order();
 *         realOrder.setId(this.getId());
 *         realOrder.setUserId(this.getUserId());
 *         return realOrder;
 *     }
 * }
 *
 * // 测试序列化
 * public class Test {
 *     public static void main(String[] args) throws Exception {
 *         // 创建懒加载代理对象（MyBatis返回的）
 *         OrderProxy proxyOrder = new OrderProxy();
 *         proxyOrder.setResultLoader(new ResultLoader(...)); // 绑定懒加载器
 *
 *         // 序列化代理对象到字节数组
 *         ByteArrayOutputStream bos = new ByteArrayOutputStream();
 *         ObjectOutputStream oos = new ObjectOutputStream(bos);
 *         oos.writeObject(proxyOrder); // 触发writeReplace()，实际序列化的是realOrder
 *         oos.close();
 *
 *         // 反序列化
 *         ByteArrayInputStream bis = new ByteArrayInputStream(bos.toByteArray());
 *         ObjectInputStream ois = new ObjectInputStream(bis);
 *         Order realOrder = (Order) ois.readObject(); // 得到真实的Order对象
 *         ois.close();
 *
 *         System.out.println(realOrder.getId()); // 正常使用，无代理相关问题
 *     }
 * }
 * 四、其他通用场景（非 MyBatis）
 * 除了 MyBatis 的懒加载，这个接口还能用于：
 * 隐藏敏感信息：比如 User 对象有 password 属性，序列化时通过writeReplace()返回一个没有 password 的 UserDTO；
 * 简化序列化对象：原对象有很多冗余属性，替换成只包含核心字段的轻量对象；
 * 版本兼容：旧对象序列化后，反序列化时用新对象替换（比如 UserV1→UserV2）。
 */
public interface WriteReplaceInterface {

  Object writeReplace() throws ObjectStreamException;

}
