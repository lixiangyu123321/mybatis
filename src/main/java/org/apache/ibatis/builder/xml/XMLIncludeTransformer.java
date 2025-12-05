/*
 * Copyright 2012 MyBatis.org.
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
package org.apache.ibatis.builder.xml;

import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.parsing.PropertyParser;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.session.Configuration;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

/**
 * @author Frank D. Martinez [mnesarco]
 */
/**
 * 是 MyBatis 中处理 Mapper XML 里 <include> 标签的核心工具类。
 * 它的核心作用是将 <include> 标签替换为对应的 <sql> 片段内容，实现 SQL 片段的复用，
 * 这是 MyBatis 简化重复 SQL 编写的关键机制。
 *
 * XMLIncludeTransformer 的核心职责：
 * 递归遍历 Mapper XML 的节点树，识别 <include> 标签；
 * 根据 <include refid="xxx"> 中的 refid 找到对应的 <sql> 片段；
 * 处理 refid 中的变量替换和命名空间拼接；
 * 将 <include> 标签替换为 <sql> 片段的内容，完成 SQL 片段的注入；
 * 支持嵌套的 <include> 标签（即 <sql> 片段中包含 <include>），递归完成替换。
 *
 * <mapper namespace="com.mybatis.mapper.UserMapper">
 *   <sql id="user_columns">
 *     id, username, create_time
 *   </sql>
 *
 *   <select id="getUserById" resultType="User">
 *     SELECT
 *       <include refid="user_columns"/>
 *     FROM user
 *     WHERE id = #{id}
 *   </select>
 * </mapper>
 */
public class XMLIncludeTransformer {

  /**
   * MyBatis全局配置对象
   * 存储所有注册的SQL片段，全局变量等核心信息
   */
  private final Configuration configuration;
  /**
   * Mapper构建助手类，负责命名空间，SQL片段引用等辅助逻辑
   */
  private final MapperBuilderAssistant builderAssistant;

  public XMLIncludeTransformer(Configuration configuration, MapperBuilderAssistant builderAssistant) {
    this.configuration = configuration;
    this.builderAssistant = builderAssistant;
  }

  /**
   * applyIncludes 执行流程
   * 遍历 <select> 节点的子节点，发现 <include refid="user_columns">；
   * 调用 findSqlFragment("user_columns")：
   * 拼接命名空间 → com.mybatis.mapper.UserMapper.user_columns；
   * 从 sqlFragments 中获取该 SQL 片段节点；
   * 克隆节点返回；
   * 递归处理该 SQL 片段（无嵌套 <include>，直接返回）；
   * 将 <include> 节点替换为 <sql id="user_columns">...</sql> 节点；
   * 将 <sql> 内的文本 id, username, create_time 移到 <select> 节点中；
   * 删除空的 <sql> 节点；
   * @param source
   */
  public void applyIncludes(Node source) {
    // 如果当前节点是<include>标签 -> 替换为对应的SQL片段
    if (source.getNodeName().equals("include")) {
      // 获取refid 属性值，找到对应的SQL片段节点
      Node toInclude = findSqlFragment(getStringAttribute(source, "refid"));
      applyIncludes(toInclude);
      // 跨文档节点处理（若 SQL 片段来自其他文档，导入当前文档）
      if (toInclude.getOwnerDocument() != source.getOwnerDocument()) {
        toInclude = source.getOwnerDocument().importNode(toInclude, true);
      }
      // 替换<include>节点为SQL片段
      source.getParentNode().replaceChild(toInclude, source);
      // 将SQL片段的子节点（即实际SQL内容）移到父节点中
      while (toInclude.hasChildNodes()) {
        toInclude.getParentNode().insertBefore(toInclude.getFirstChild(), toInclude);
      }
      // 移除空的SQL片段节点
      toInclude.getParentNode().removeChild(toInclude);
    } else if (source.getNodeType() == Node.ELEMENT_NODE) {
      // 如果当前节点是元素节点，（如 <select>/<insert>/<sql> 等）→ 递归处理子节点
      NodeList children = source.getChildNodes();
      for (int i=0; i<children.getLength(); i++) {
        applyIncludes(children.item(i));
      }
    }
  }

  private Node findSqlFragment(String refid) {
    // 解析 refid 中的变量（如 ${namespace}.user_columns）
    refid = PropertyParser.parse(refid, configuration.getVariables());
    // 步骤2：拼接当前命名空间（若 refid 无命名空间，补充当前 Mapper 的命名空间）
    refid = builderAssistant.applyCurrentNamespace(refid, true);
    try {
      // 步骤3：从全局配置的 sqlFragments 中获取对应的 SQL 片段节点
      XNode nodeToInclude = configuration.getSqlFragments().get(refid);
      // 步骤4：克隆节点（避免修改原 SQL 片段）
      return nodeToInclude.getNode().cloneNode(true);
    } catch (IllegalArgumentException e) {
      throw new IncompleteElementException("Could not find SQL statement to include with refid '" + refid + "'", e);
    }
  }

  private String getStringAttribute(Node node, String name) {
    return node.getAttributes().getNamedItem(name).getNodeValue();
  }
}
