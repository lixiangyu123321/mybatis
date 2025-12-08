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
package org.apache.ibatis.builder.xml;

import java.util.List;
import java.util.Locale;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.executor.keygen.Jdbc3KeyGenerator;
import org.apache.ibatis.executor.keygen.KeyGenerator;
import org.apache.ibatis.executor.keygen.NoKeyGenerator;
import org.apache.ibatis.executor.keygen.SelectKeyGenerator;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.ResultSetType;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.mapping.SqlSource;
import org.apache.ibatis.mapping.StatementType;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.scripting.LanguageDriver;
import org.apache.ibatis.session.Configuration;

/**
 * @author Clinton Begin
 */
/**
 * XML语句构建器，建造者模式,继承BaseBuilder
 * XMLStatementBuilder 是 MyBatis 解析 Mapper XML 文件中 <select>/<insert>/<update>/<delete> 标签的核心类，
 * 它会将这些标签的属性和内容解析为 MyBatis 内部的 MappedStatement 对象（封装 SQL 执行的所有配置），是 MyBatis 从 XML 配置到可执行 SQL 的关键桥梁。
 */
public class XMLStatementBuilder extends BaseBuilder {

  /**
   * Mapper构建助手类，封装了MappedStatement的创建/注册逻辑（解耦核心解析和对象构建）
   * MapperBuilderAssistant 是 “助手类”，核心作用是封装 MappedStatement 的创建和注册，避免 XMLStatementBuilder 逻辑过于臃肿。
   */
  private MapperBuilderAssistant builderAssistant;
  /**
   * 当前解析的SQL节点（<select>/<insert>等），封装了XML节点的属性和内容
   */
  private XNode context;
  /**
   * 匹配的数据库ID（使用何种数据库）
   */
  private String requiredDatabaseId;

  /**
   * 无databaseId的构造器（默认适配所有数据库）
   * @param configuration
   * @param builderAssistant
   * @param context
   */
  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context) {
    this(configuration, builderAssistant, context, null);
  }

  /**
   * 带databaseId的构造器（适配指定数据库）
   * @param configuration
   * @param builderAssistant
   * @param context
   * @param databaseId
   */
  public XMLStatementBuilder(Configuration configuration, MapperBuilderAssistant builderAssistant, XNode context, String databaseId) {
    // 初始化BaseBuilder的核心配置（Configuration是MyBatis的全局配置）
    super(configuration);
    this.builderAssistant = builderAssistant;
    this.context = context;
    this.requiredDatabaseId = databaseId;
  }


  /**
   * 解析SQL节点的主流程
   * 将前面解析的所有配置（SQL 内容、参数类型、结果类型、缓存配置、主键生成器等）封装为 MappedStatement 对象，
   * 并注册到 Configuration 的 mappedStatements 集合中；
   */
  public void parseStatementNode() {
    String id = context.getStringAttribute("id");
    String databaseId = context.getStringAttribute("databaseId");

    // 核心校验：如果当前数据库ID不匹配，直接退出解析（多数据库适配核心逻辑）
    if (!databaseIdMatchesCurrent(id, databaseId, this.requiredDatabaseId)) {
      return;
    }

    // 数据库每次批量返回的结果行数（优化查询性能）
    Integer fetchSize = context.getIntAttribute("fetchSize");
    // SQL执行超时时间（毫秒）
    Integer timeout = context.getIntAttribute("timeout");
    // 废弃的parameterMap（MyBatis早期参数映射，已被parameterType替代）
    String parameterMap = context.getStringAttribute("parameterMap");
    // 参数类型（如int/com.example.entity.User）
    String parameterType = context.getStringAttribute("parameterType");
    Class<?> parameterTypeClass = resolveClass(parameterType);
    // 结果映射（高级功能，自定义字段和实体属性的映射）
    String resultMap = context.getStringAttribute("resultMap");
    // 结果类型（如hashmap/com.example.entity.User）
    String resultType = context.getStringAttribute("resultType");
    // 脚本语言（MyBatis 3.2+新功能，默认OGNL，支持自定义）
    String lang = context.getStringAttribute("lang");
    // 获取语言驱动（解析动态SQL）
    LanguageDriver langDriver = getLanguageDriver(lang);
    Class<?> resultTypeClass = resolveClass(resultType);

    // 结果集类型（FORWARD_ONLY：只能向前滚动；SCROLL_INSENSITIVE：可滚动，不敏感；SCROLL_SENSITIVE：可滚动，敏感）
    String resultSetType = context.getStringAttribute("resultSetType");
    // 语句类型（STATEMENT：静态SQL；PREPARED：预编译SQL（默认）；CALLABLE：存储过程）
    StatementType statementType = StatementType.valueOf(context.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    // 解析为枚举类型
    ResultSetType resultSetTypeEnum = resolveResultSetType(resultSetType);

    // 获取命令类型(select|insert|update|delete)， 并解析为枚举类型
    String nodeName = context.getNode().getNodeName();
    SqlCommandType sqlCommandType = SqlCommandType.valueOf(nodeName.toUpperCase(Locale.ENGLISH));


    boolean isSelect = sqlCommandType == SqlCommandType.SELECT;
    boolean flushCache = context.getBooleanAttribute("flushCache", !isSelect);
    //是否要缓存select结果
    boolean useCache = context.getBooleanAttribute("useCache", isSelect);
    // 结果有序：仅嵌套结果集生效，避免内存溢出
    boolean resultOrdered = context.getBooleanAttribute("resultOrdered", false);

    //解析之前先解析<include>SQL片段
    XMLIncludeTransformer includeParser = new XMLIncludeTransformer(configuration, builderAssistant);
    includeParser.applyIncludes(context.getNode());

    // 解析之前先解析<selectKey>
    processSelectKeyNodes(id, parameterTypeClass, langDriver);

    // 将XML中的SQL内容（含动态标签）解析为SqlSource
    SqlSource sqlSource = langDriver.createSqlSource(configuration, context, parameterTypeClass);

    // 多结果集名称（仅多结果集场景使用）
    String resultSets = context.getStringAttribute("resultSets");
    // 主键属性（如user.id）
    //(仅对 insert 有用) 标记一个属性, MyBatis 会通过 getGeneratedKeys 或者通过 insert 语句的 selectKey 子元素设置它的值
    String keyProperty = context.getStringAttribute("keyProperty");
    // 主键列（如id）
    //(仅对 insert 有用) 标记一个属性, MyBatis 会通过 getGeneratedKeys 或者通过 insert 语句的 selectKey 子元素设置它的值
    String keyColumn = context.getStringAttribute("keyColumn");

    // 主键生成器
    /**
     * KeyGenerator：主键生成器接口，实现类包括：
     * Jdbc3KeyGenerator：基于 JDBC getGeneratedKeys() 获取自增主键（MySQL）；
     * SelectKeyGenerator：基于 <selectKey> 标签的 SQL 获取主键（Oracle）；
     * NoKeyGenerator：无主键生成（默认）。
     */
    KeyGenerator keyGenerator;
    String keyStatementId = id + SelectKeyGenerator.SELECT_KEY_SUFFIX;
    keyStatementId = builderAssistant.applyCurrentNamespace(keyStatementId, true);
    if (configuration.hasKeyGenerator(keyStatementId)) {
      keyGenerator = configuration.getKeyGenerator(keyStatementId);
    } else {
      keyGenerator = context.getBooleanAttribute("useGeneratedKeys",
          configuration.isUseGeneratedKeys() && SqlCommandType.INSERT.equals(sqlCommandType))
          ? new Jdbc3KeyGenerator() : new NoKeyGenerator();
    }

    // 调用助手类，创建并注册MappedStatement到Configuration中
    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered, 
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, resultSets);
  }

  /**
   * 该方法是 MyBatis 解析 Mapper XML 中 <selectKey> 标签的入口方法，核心完成三件事 ——
   * 查找所有 <selectKey> 节点、
   * 按数据库 ID 适配解析节点、
   * 解析完成后移除节点避免重复处理，
   * 是 MyBatis 实现主键生成的关键前置步骤。
   *
   * 先明确 <selectKey> 的核心作用（理解方法的业务背景）
   * <selectKey> 是 MyBatis 用于解决不同数据库主键生成的标签，比如：
   * MySQL 自增主键：插入后通过 SELECT LAST_INSERT_ID() 获取；
   * Oracle 序列：插入前通过 SELECT SEQ_USER.NEXTVAL FROM DUAL 获取；
   *
   * <insert id="insertUser">
   *   <selectKey keyProperty="id" order="AFTER" resultType="java.lang.Long">
   *     SELECT LAST_INSERT_ID()
   *   </selectKey>
   *   INSERT INTO user(name) VALUES (#{name})
   * </insert>
   * @param id
   * @param parameterTypeClass
   * @param langDriver
   */
  private void processSelectKeyNodes(String id, Class<?> parameterTypeClass, LanguageDriver langDriver) {
    List<XNode> selectKeyNodes = context.evalNodes("selectKey");
    if (configuration.getDatabaseId() != null) {
      parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, configuration.getDatabaseId());
    }
    parseSelectKeyNodes(id, selectKeyNodes, parameterTypeClass, langDriver, null);
    removeSelectKeyNodes(selectKeyNodes);
  }

  /**
   * 该方法是 <selectKey> 节点的批量解析分发器，核心逻辑是遍历所有 <selectKey> 节点，
   * 先为每个节点生成唯一 ID、校验数据库 ID 匹配性，仅对匹配的节点执行真正的解析逻辑（parseSelectKeyNode），
   * 是多数据库适配下 <selectKey> 解析的核心控制层。
   * @param parentId 主SQL节点的ID（如insertUser）
   * @param list 待解析的<selectKey>节点列表
   * @param parameterTypeClass 主SQL的参数类型（如User.class）
   * @param langDriver 语言驱动（解析动态SQL）
   * @param skRequiredDatabaseId 期望匹配的数据库ID（如mysql/oracle/null）
   */
  private void parseSelectKeyNodes(String parentId, List<XNode> list, Class<?> parameterTypeClass, LanguageDriver langDriver, String skRequiredDatabaseId) {
    for (XNode nodeToHandle : list) {
      String id = parentId + SelectKeyGenerator.SELECT_KEY_SUFFIX;
      String databaseId = nodeToHandle.getStringAttribute("databaseId");
      if (databaseIdMatchesCurrent(id, databaseId, skRequiredDatabaseId)) {
        // 执行单个<selectKey>的解析逻辑
        parseSelectKeyNode(id, nodeToHandle, parameterTypeClass, langDriver, databaseId);
      }
    }
  }

  /**
   * 该方法是 MyBatis 解析单个 <selectKey> 标签的核心实现，会将 <selectKey> 标签的所有属性解析为独立的 MappedStatement，
   * 并封装成 SelectKeyGenerator（主键生成器）注册到全局配置中，是 MyBatis 实现自定义主键生成逻辑的关键。
   * @param id <selectKey>的唯一ID（如insertUser_SELECT_KEY）
   * @param nodeToHandle 当前解析的<selectKey>节点
   * @param parameterTypeClass 主SQL的参数类型（如User.class）
   * @param langDriver 语言驱动（解析动态SQL）
   * @param databaseId 当前<selectKey>适配的数据库ID（如mysql/oracle）
   */
  private void parseSelectKeyNode(String id, XNode nodeToHandle, Class<?> parameterTypeClass, LanguageDriver langDriver, String databaseId) {
    String resultType = nodeToHandle.getStringAttribute("resultType");
    Class<?> resultTypeClass = resolveClass(resultType);
    StatementType statementType = StatementType.valueOf(nodeToHandle.getStringAttribute("statementType", StatementType.PREPARED.toString()));
    String keyProperty = nodeToHandle.getStringAttribute("keyProperty");
    String keyColumn = nodeToHandle.getStringAttribute("keyColumn");

    // 主键生成时机，默认插入后执行
    boolean executeBefore = "BEFORE".equals(nodeToHandle.getStringAttribute("order", "AFTER"));

    /**
     * <selectKey> 是特殊的查询语句（仅用于获取主键），不需要缓存、超时、结果映射等复杂配置，因此直接设置固定默认值，简化开发者配置。
     */
    boolean useCache = false;
    boolean resultOrdered = false;
    KeyGenerator keyGenerator = new NoKeyGenerator();
    Integer fetchSize = null;
    Integer timeout = null;
    boolean flushCache = false;
    String parameterMap = null;
    String resultMap = null;
    ResultSetType resultSetTypeEnum = null;

    /**
     * 创建并注册 <selectKey> 对应的 MappedStatement
     */
    SqlSource sqlSource = langDriver.createSqlSource(configuration, nodeToHandle, parameterTypeClass);
    SqlCommandType sqlCommandType = SqlCommandType.SELECT;

    builderAssistant.addMappedStatement(id, sqlSource, statementType, sqlCommandType,
        fetchSize, timeout, parameterMap, parameterTypeClass, resultMap, resultTypeClass,
        resultSetTypeEnum, flushCache, useCache, resultOrdered,
        keyGenerator, keyProperty, keyColumn, databaseId, langDriver, null);

    /**
     * 封装并注册 SelectKeyGenerator（核心收尾）
     */
    id = builderAssistant.applyCurrentNamespace(id, false);

    MappedStatement keyStatement = configuration.getMappedStatement(id, false);
    configuration.addKeyGenerator(id, new SelectKeyGenerator(keyStatement, executeBefore));
  }

  /**
   * 该方法是 MyBatis 解析完 <selectKey> 标签后，从 XML 节点树中移除这些节点的清理操作，
   * 核心目的是避免 <selectKey> 节点被重复解析、防止主 SQL 拼接错误，同时精简后续 XML 处理的节点结构。
   * @param selectKeyNodes
   */
  private void removeSelectKeyNodes(List<XNode> selectKeyNodes) {
    for (XNode nodeToHandle : selectKeyNodes) {
      // 从父节点中移除<selectKey>节点
      nodeToHandle.getParent().getNode().removeChild(nodeToHandle.getNode());
    }
  }

  /**
   * 该方法是 MyBatis 多数据库适配场景下的核心校验器，
   * 用于判断当前解析的 SQL 节点（或 <selectKey> 节点）的 databaseId 是否匹配 “期望的数据库 ID”，
   * 保证每个 SQL ID 只会加载适配当前数据库的版本，避免重复或冲突。
   * @param id 当前节点的ID（如insertUser/insertUser_SELECT_KEY）
   * @param databaseId 当前节点的databaseId属性（如mysql/oracle/null）
   * @param requiredDatabaseId 期望匹配的数据库ID（如mysql/null）
   * @return
   */
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    // 分支 1：requiredDatabaseId ≠ null（期望匹配指定数据库 ID）
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      // 分支 2：requiredDatabaseId = null（期望匹配 “无 databaseId” 的节点）
      if (databaseId != null) {
        return false;
      }
      // 给ID加命名空间
      id = builderAssistant.applyCurrentNamespace(id, false);
      // 检查是否已有该ID的MappedStatement
      if (this.configuration.hasStatement(id, false)) {
        MappedStatement previous = this.configuration.getMappedStatement(id, false);
        if (previous.getDatabaseId() != null) {
          return false;
        }
      }
    }
    return true;
  }

  //取得语言驱动
  private LanguageDriver getLanguageDriver(String lang) {
    Class<?> langClass = null;
    if (lang != null) {
      langClass = resolveClass(lang);
    }
    //调用builderAssistant
    return builderAssistant.getLanguageDriver(langClass);
  }

}
