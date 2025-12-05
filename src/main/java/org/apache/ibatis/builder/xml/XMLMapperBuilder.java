/*
 *    Copyright 2009-2013 the original author or authors.
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

import java.io.InputStream;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.ibatis.builder.BaseBuilder;
import org.apache.ibatis.builder.BuilderException;
import org.apache.ibatis.builder.CacheRefResolver;
import org.apache.ibatis.builder.IncompleteElementException;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.apache.ibatis.builder.ResultMapResolver;
import org.apache.ibatis.cache.Cache;
import org.apache.ibatis.executor.ErrorContext;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Discriminator;
import org.apache.ibatis.mapping.ParameterMapping;
import org.apache.ibatis.mapping.ParameterMode;
import org.apache.ibatis.mapping.ResultFlag;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.parsing.XNode;
import org.apache.ibatis.parsing.XPathParser;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.type.JdbcType;
import org.apache.ibatis.type.TypeHandler;

/**
 * @author Clinton Begin
 */
/**
 *XMLMapperBuilder 是 MyBatis 解析 Mapper XML 文件的核心类，
 * 它负责将 Mapper XML 中的所有节点（如 <select>/<resultMap>/<sql> 等）解析并注册到 MyBatis 全局配置 Configuration 中，
 * 是 MyBatis 加载 SQL 映射的核心入口。
 */
public class XMLMapperBuilder extends BaseBuilder {

  /**
   * XPath解析器：用于解析XML节点
   */
  private XPathParser parser;
  /**
   * Mapper构建助手，封装命名空间，缓存，ResultMap等构建逻辑，简化XMLMapperBuilder的代码
   */
  private MapperBuilderAssistant builderAssistant;
  /**
   * SQL片段缓存
   */
  private Map<String, XNode> sqlFragments;
  /**
   * 当前解析的Mapper资源路径，用于标记是否已加载，防止重复解析
   */
  private String resource;

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(reader, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  @Deprecated
  public XMLMapperBuilder(Reader reader, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(reader, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments, String namespace) {
    this(inputStream, configuration, resource, sqlFragments);
    this.builderAssistant.setCurrentNamespace(namespace);
  }

  public XMLMapperBuilder(InputStream inputStream, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    this(new XPathParser(inputStream, true, configuration.getVariables(), new XMLMapperEntityResolver()),
        configuration, resource, sqlFragments);
  }

  /**
   * 所有的构造方法都以来于此方法
   * @param parser
   * @param configuration
   * @param resource
   * @param sqlFragments
   */
  private XMLMapperBuilder(XPathParser parser, Configuration configuration, String resource, Map<String, XNode> sqlFragments) {
    super(configuration);
    this.builderAssistant = new MapperBuilderAssistant(configuration, resource);
    this.parser = parser;
    this.sqlFragments = sqlFragments;
    this.resource = resource;
  }

  /**
   * 核心入口方法
   */
  public void parse() {
    if (!configuration.isResourceLoaded(resource)) {
      // 解析<mapper>下的所有子节点（select/resultMap/sql等）
      configurationElement(parser.evalNode("/mapper"));
      // 标记该资源已被加载，避免重复解析
      configuration.addLoadedResource(resource);
      // 绑定Mapper接口与命名空间（如namespace对应UserMapper接口）
      bindMapperForNamespace();
    }
    /**
     * 补全未完成的解析项
     * 补全未解析的ResultMap
     * 补全未解析的缓存引用
     * 补全未解析的SQL语句
     */
    parsePendingResultMaps();
    parsePendingChacheRefs();
    parsePendingStatements();
  }

  /**
   * 获得SQL片段
   * @param refid
   * @return
   */
  public XNode getSqlFragment(String refid) {
    return sqlFragments.get(refid);
  }

  /**
   * 按固定顺序解析所有子节点
   * @param context
   */
  private void configurationElement(XNode context) {
    try {
      // 解析命名空间
      String namespace = context.getStringAttribute("namespace");
      if (namespace.equals("")) {
        throw new BuilderException("Mapper's namespace cannot be empty");
      }
      builderAssistant.setCurrentNamespace(namespace);
      // 解析<cache-ref>（引用其他Mapper的缓存配置）
      cacheRefElement(context.evalNode("cache-ref"));
      // 解析<cache>（当前Mapper的缓存配置）
      cacheElement(context.evalNode("cache"));
      // 解析<parameterMap>（已废弃，老式参数映射，兼容用）
      parameterMapElement(context.evalNodes("/mapper/parameterMap"));
      // 解析<resultMap>（结果集映射，MyBatis核心高级功能）
      resultMapElements(context.evalNodes("/mapper/resultMap"));
      // 解析<sql>（SQL片段，供<include>复用）
      sqlElement(context.evalNodes("/mapper/sql"));
      // 解析<select|insert|update|delete>（核心SQL语句）
      buildStatementFromContext(context.evalNodes("select|insert|update|delete"));
    } catch (Exception e) {
      throw new BuilderException("Error parsing Mapper XML. Cause: " + e, e);
    }
  }

  /**
   * 按数据库方言优先级（databaseId）优先级解析SQL语句
   * <mapper namespace="com.mybatis.mapper.UserMapper">
   *   <!-- MySQL专用SQL（databaseId="mysql"） -->
   *   <select id="getUserById" resultType="User" databaseId="mysql">
   *     SELECT id, username, create_time FROM user WHERE id = #{id}
   *   </select>
   *
   *   <!-- Oracle专用SQL（databaseId="oracle"） -->
   *   <select id="getUserById" resultType="User" databaseId="oracle">
   *     SELECT id, username, create_time FROM user WHERE id = #{id}
   *     AND ROWNUM = 1
   *   </select>
   *
   *   <!-- 默认SQL（无databaseId） -->
   *   <select id="getUserById" resultType="User">
   *     SELECT id, username, create_time FROM user WHERE id = #{id}
   *   </select>
   * </mapper>
   * @param list
   */
  private void buildStatementFromContext(List<XNode> list) {
    if (configuration.getDatabaseId() != null) {
      /**
       * configuration.getDatabaseId() != null：判断是否已识别出当前数据库的方言（如连接 MySQL 后，databaseId 为 mysql）；
       * 调用重载方法 buildStatementFromContext(list, requiredDatabaseId)，传入当前数据库的 databaseId（如 mysql）；
       * 核心逻辑：只解析 SQL 节点中 databaseId 属性等于 requiredDatabaseId 的语句（如 <select databaseId="mysql" ...>）；
       * 设计意图：优先加载适配当前数据库的 SQL 语句，保证兼容性和性能
       */
      buildStatementFromContext(list, configuration.getDatabaseId());
    }
    /**
     * 调用重载方法 buildStatementFromContext(list, null)，传入 null 表示匹配「无 databaseId 属性」的 SQL 语句；
     * 核心逻辑：只解析 SQL 节点中未配置 databaseId 的语句（如 <select ...> 无 databaseId 属性）；
     * 设计意图：作为兜底，当没有适配当前数据库的 SQL 时，使用默认 SQL 语句。
     */
    buildStatementFromContext(list, null);
  }

  /**
   * 基于SQL的id解析匹配对应的数据库方言
   * @param list
   * @param requiredDatabaseId
   */
  private void buildStatementFromContext(List<XNode> list, String requiredDatabaseId) {
    for (XNode context : list) {
      // 创建SQL语句解析器（核心逻辑封装再XMLStatementBuilder）
      final XMLStatementBuilder statementParser = new XMLStatementBuilder(configuration, builderAssistant, context, requiredDatabaseId);
      try {
        // 解析SQL节点（id/parameterType/resultType等）
        statementParser.parseStatementNode();
      } catch (IncompleteElementException e) {
        // 出现异常，将对应XML解析器放入configuration，用于后续重试
        configuration.addIncompleteStatement(statementParser);
      }
    }
  }

  /**
   * 补全未完成 ResultMap 解析的核心方法
   */
  private void parsePendingResultMaps() {
    Collection<ResultMapResolver> incompleteResultMaps = configuration.getIncompleteResultMaps();
    synchronized (incompleteResultMaps) {
      Iterator<ResultMapResolver> iter = incompleteResultMaps.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolve();
          iter.remove();
        } catch (IncompleteElementException e) {
        }
      }
    }
  }

  /**
   * 补全未完成 chacheRef解析
   */
  private void parsePendingChacheRefs() {
    Collection<CacheRefResolver> incompleteCacheRefs = configuration.getIncompleteCacheRefs();
    synchronized (incompleteCacheRefs) {
      Iterator<CacheRefResolver> iter = incompleteCacheRefs.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().resolveCacheRef();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Cache ref is still missing a resource...
        }
      }
    }
  }

  /**
   * 补全未完成 SQL 语句解析的核心方法
   */
  private void parsePendingStatements() {
    Collection<XMLStatementBuilder> incompleteStatements = configuration.getIncompleteStatements();
    synchronized (incompleteStatements) {
      Iterator<XMLStatementBuilder> iter = incompleteStatements.iterator();
      while (iter.hasNext()) {
        try {
          iter.next().parseStatementNode();
          iter.remove();
        } catch (IncompleteElementException e) {
          // Statement is still missing a resource...
        }
      }
    }
  }

  /**
   * 解析 <cache-ref> 标签的核心方法，
   * 它实现了 MyBatis 中「Mapper 缓存引用」的功能（即一个 Mapper 复用另一个 Mapper 的缓存配置），
   * 同时处理引用缓存未加载的依赖问题。
   *
   * <!-- UserMapper.xml -->
   * <mapper namespace="com.mybatis.mapper.UserMapper">
   *   <cache-ref namespace="com.mybatis.mapper.RoleMapper"/>
   *   <!-- 其他节点：select/resultMap等 -->
   * </mapper>
   *
   * <!-- RoleMapper.xml -->
   * <mapper namespace="com.mybatis.mapper.RoleMapper">
   *   <cache eviction="LRU" flushInterval="60000" size="512"/>
   *   <!-- 其他节点 -->
   * </mapper>
   * @param context
   */
  private void cacheRefElement(XNode context) {
    if (context != null) {
      /**
       * <cache-ref namespace="com.mybatis.mapper.RoleMapper"/>
       */
      // 注册缓存引用关系到Configuration
      configuration.addCacheRef(builderAssistant.getCurrentNamespace(), context.getStringAttribute("namespace"));
      // 创建缓存引用解析器
      CacheRefResolver cacheRefResolver = new CacheRefResolver(builderAssistant, context.getStringAttribute("namespace"));
      try {
        // 缓存引用解析
        cacheRefResolver.resolveCacheRef();
      } catch (IncompleteElementException e) {
        configuration.addIncompleteCacheRef(cacheRefResolver);
      }
    }
  }


  /**
   * 是 XMLMapperBuilder 中解析 <cache> 标签的核心方法，
   * 它负责解析 Mapper 级别的缓存配置（如缓存类型、淘汰策略、刷新间隔等），
   * 并通过 MapperBuilderAssistant 构建缓存对象注册到 Configuration 中，
   * 是 MyBatis 一级 / 二级缓存体系中「二级缓存」的核心配置解析入口
   *
   * <cache
   *   type="PERPETUAL"
   *   eviction="LRU"
   *   flushInterval="60000"
   *   size="512"
   *   readOnly="false"
   *   blocking="false">
   *   <property name="cacheKey" value="myKey"/>
   * </cache>
   * @param context
   * @throws Exception
   */
  private void cacheElement(XNode context) throws Exception {
    if (context != null) {
      String type = context.getStringAttribute("type", "PERPETUAL");
      /**
       * context.getStringAttribute("type", "PERPETUAL")：获取 type 属性，无则使用默认值 PERPETUAL；
       * typeAliasRegistry.resolveAlias(type)：将别名解析为实际类（MyBatis 内置别名映射）：
       * PERPETUAL → org.apache.ibatis.cache.impl.PerpetualCache（永久缓存，核心实现）；
       * 也可自定义缓存类型（如 Redis/Memcached 缓存，需自定义实现 Cache 接口并注册别名）；
       */
      Class<? extends Cache> typeClass = typeAliasRegistry.resolveAlias(type);
      String eviction = context.getStringAttribute("eviction", "LRU");
      // 与上同理，将别名解析为实际类
      Class<? extends Cache> evictionClass = typeAliasRegistry.resolveAlias(eviction);
      Long flushInterval = context.getLongAttribute("flushInterval");
      Integer size = context.getIntAttribute("size");
      boolean readWrite = !context.getBooleanAttribute("readOnly", false);
      /**
       * blocking：阻塞模式，默认 false；
       * true：当缓存中无数据时，多个线程同时查询同一数据，只有一个线程会去数据库查询，其他线程阻塞等待，避免缓存击穿；
       * false：多个线程同时查询同一数据，都会去数据库查询（可能导致数据库压力骤增）。
       */
      boolean blocking = context.getBooleanAttribute("blocking", false);
      Properties props = context.getChildrenAsProperties();
      builderAssistant.useNewCache(typeClass, evictionClass, flushInterval, size, readWrite, blocking, props);
    }
  }


  /**
   * MyBatis 为兼容老式参数映射配置保留的逻辑（现已废弃），
   * 核心作用是将 XML 中 <parameterMap> 节点解析为 ParameterMap 对象并注册到 Configuration 中。
   *
   * <!-- 老式 parameterMap 配置（现已废弃） -->
   * <parameterMap id="userParamMap" type="com.mybatis.entity.User">
   *   <parameter property="id" javaType="java.lang.Integer" jdbcType="INTEGER" mode="IN"/>
   *   <parameter property="username" javaType="java.lang.String" jdbcType="VARCHAR" typeHandler="org.apache.ibatis.type.StringTypeHandler"/>
   * </parameterMap>
   *
   * <!-- 使用 parameterMap 的 SQL（老式写法） -->
   * <insert id="insertUser" parameterMap="userParamMap">
   *   INSERT INTO user(id, username) VALUES (?, ?)
   * </insert>
   * @param list
   * @throws Exception
   */
  private void parameterMapElement(List<XNode> list) throws Exception {
    for (XNode parameterMapNode : list) {
      String id = parameterMapNode.getStringAttribute("id");
      String type = parameterMapNode.getStringAttribute("type");
      // 将类型字符串转换成实际类
      Class<?> parameterClass = resolveClass(type);
      // 获取<parameterMap>下的所有<parameter>子节点
      List<XNode> parameterNodes = parameterMapNode.evalNodes("parameter");
      // 初始化ParameterMapping列表（存储每个参数的映射规则）
      List<ParameterMapping> parameterMappings = new ArrayList<ParameterMapping>();
      for (XNode parameterNode : parameterNodes) {
        // 对应属性名，即Java Bean的字段
        String property = parameterNode.getStringAttribute("property");
        String javaType = parameterNode.getStringAttribute("javaType");
        String jdbcType = parameterNode.getStringAttribute("jdbcType");
        String resultMap = parameterNode.getStringAttribute("resultMap");
        // 参数模式，用于存储过程
        String mode = parameterNode.getStringAttribute("mode");
        // 类型处理器
        String typeHandler = parameterNode.getStringAttribute("typeHandler");
        // 数值精度
        Integer numericScale = parameterNode.getIntAttribute("numericScale");
        ParameterMode modeEnum = resolveParameterMode(mode);
        Class<?> javaTypeClass = resolveClass(javaType);
        JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
        @SuppressWarnings("unchecked")
        Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
        ParameterMapping parameterMapping = builderAssistant.buildParameterMapping(parameterClass, property, javaTypeClass, jdbcTypeEnum, resultMap, modeEnum, typeHandlerClass, numericScale);
        parameterMappings.add(parameterMapping);
      }
      // 添加参数映射到Configuration中
      builderAssistant.addParameterMap(id, parameterClass, parameterMappings);
    }
  }

  private void resultMapElements(List<XNode> list) throws Exception {
    for (XNode resultMapNode : list) {
      try {
        resultMapElement(resultMapNode);
      } catch (IncompleteElementException e) {
      }
    }
  }

  private ResultMap resultMapElement(XNode resultMapNode) throws Exception {
    return resultMapElement(resultMapNode, Collections.<ResultMapping> emptyList());
  }

  /**
   * XMLMapperBuilder 中解析 <resultMap> 标签的核心方法，
   * 它负责将 XML 中的 <resultMap> 节点（包含主键、普通字段、构造器、鉴别器等）解析为 MyBatis 内部的 ResultMap 对象，
   * 是 MyBatis 结果集映射（ORM 核心）的核心实现。
   *
   * <resultMap id="UserResultMap" type="com.mybatis.entity.User" extends="BaseResultMap" autoMapping="true">
   *   <!-- 主键映射 -->
   *   <id column="id" property="id" javaType="java.lang.Integer"/>
   *   <!-- 普通字段映射 -->
   *   <result column="username" property="username" jdbcType="VARCHAR"/>
   *   <!-- 构造器映射（用于无参构造器缺失的场景） -->
   *   <constructor>
   *     <idArg column="id" property="id" javaType="java.lang.Integer"/>
   *     <arg column="username" property="username" javaType="java.lang.String"/>
   *   </constructor>
   *   <!-- 鉴别器（多态映射） -->
   *   <discriminator column="user_type" javaType="java.lang.String">
   *     <case value="ADMIN" resultType="com.mybatis.entity.AdminUser"/>
   *     <case value="NORMAL" resultType="com.mybatis.entity.NormalUser"/>
   *   </discriminator>
   * </resultMap>
   * @param resultMapNode
   * @param additionalResultMappings
   * @return
   * @throws Exception
   */
  private ResultMap resultMapElement(XNode resultMapNode, List<ResultMapping> additionalResultMappings) throws Exception {
    // 错误上下文，便于异常是定位信息
    ErrorContext.instance().activity("processing " + resultMapNode.getValueBasedIdentifier());
    // 解析id（优先取id属性，无则用节点唯一标识）
    String id = resultMapNode.getStringAttribute("id",
        resultMapNode.getValueBasedIdentifier());
    // 解析结果类型（优先级：type > ofType > resultType > javaTyp）
    String type = resultMapNode.getStringAttribute("type",
        resultMapNode.getStringAttribute("ofType",
            resultMapNode.getStringAttribute("resultType",
                resultMapNode.getStringAttribute("javaType"))));
    // 解析继承的父ResultMap
    String extend = resultMapNode.getStringAttribute("extends");
    // 解析自动映射开关，即是否自动映射为显示配置的字段（如数据库字段 email 对应 Java 对象 email 字段，无需手动配置）
    Boolean autoMapping = resultMapNode.getBooleanAttribute("autoMapping");
    // 将类型字符串转换为Class对象
    Class<?> typeClass = resolveClass(type);
    // 鉴别器，用于多态结果映射
    Discriminator discriminator = null;

    // 结果映射列表，存储所有映射规则（存储 <id>/<result>/<constructor> 等所有映射规则）
    List<ResultMapping> resultMappings = new ArrayList<ResultMapping>();
    // 用于嵌套映射场景，如如 <association> 传递父级映射
    resultMappings.addAll(additionalResultMappings);
    List<XNode> resultChildren = resultMapNode.getChildren();
    for (XNode resultChild : resultChildren) {
      // 处理所有映射场景
      if ("constructor".equals(resultChild.getName())) {
        processConstructorElement(resultChild, typeClass, resultMappings);
      } else if ("discriminator".equals(resultChild.getName())) {
        discriminator = processDiscriminatorElement(resultChild, typeClass, resultMappings);
      } else {
        // 解析 <id>/<result> 节点（主键/普通字段映射）
        List<ResultFlag> flags = new ArrayList<ResultFlag>();
        if ("id".equals(resultChild.getName())) {
          // 标记为主键字段（ResultFlag.ID），MyBatis 主键处理优先级更高
          flags.add(ResultFlag.ID);
        }
        // 构建单个ResultMapping对象并添加到列表中
        resultMappings.add(buildResultMappingFromContext(resultChild, typeClass, flags));
      }
    }
    // ResultMapResolver：封装 ResultMap 的构建和注册逻辑，解耦解析和构建过程。
    ResultMapResolver resultMapResolver = new ResultMapResolver(builderAssistant, id, typeClass, extend, discriminator, resultMappings, autoMapping);
    try {
      // 禅师解析并构建ResultMap
      return resultMapResolver.resolve();
    } catch (IncompleteElementException  e) {
      configuration.addIncompleteResultMap(resultMapResolver);
      throw e;
    }
  }

  /**
   * 它专门处理 <resultMap> 下的构造器映射，用于解决 Java 实体类无默认构造器（无参构造） 时的结果集映射问题。
   * 核心背景：若 Java 实体类只有有参构造器（无默认无参构造），MyBatis 无法通过 new User() 创建对象，
   * 需通过 <constructor> 配置构造器参数，从结果集取值并调用构造器实例化对象；
   *
   * <resultMap id="UserResultMap" type="com.mybatis.entity.User">
   *   <constructor>
   *     <!-- 主键构造参数（idArg） -->
   *     <idArg column="id" javaType="java.lang.Integer" name="id"/>
   *     <!-- 普通构造参数（arg） -->
   *     <arg column="username" javaType="java.lang.String" name="username"/>
   *   </constructor>
   * </resultMap>
   * @param resultChild
   * @param resultType
   * @param resultMappings
   * @throws Exception
   */
  private void processConstructorElement(XNode resultChild, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    List<XNode> argChildren = resultChild.getChildren();
    for (XNode argChild : argChildren) {
      List<ResultFlag> flags = new ArrayList<ResultFlag>();
      // 标记为构造器参数
      flags.add(ResultFlag.CONSTRUCTOR);
      // 额外标记为主键
      if ("idArg".equals(argChild.getName())) {
        flags.add(ResultFlag.ID);
      }
      resultMappings.add(buildResultMappingFromContext(argChild, resultType, flags));
    }
  }

  /**
   * XMLMapperBuilder 中解析 <discriminator>（鉴别器）节点的核心方法 —— 它专门处理 MyBatis 结果映射中的多态场景，
   * 能根据数据库字段的不同值，动态选择不同的 ResultMap 来映射结果集（比如将 user_type=ADMIN 的记录映射为 AdminUser，user_type=NORMAL 映射为 NormalUser）。
   * @param context
   * @param resultType
   * @param resultMappings
   * @return
   * @throws Exception
   */
  private Discriminator processDiscriminatorElement(XNode context, Class<?> resultType, List<ResultMapping> resultMappings) throws Exception {
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    String typeHandler = context.getStringAttribute("typeHandler");
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    Map<String, String> discriminatorMap = new HashMap<String, String>();
    for (XNode caseChild : context.getChildren()) {
      String value = caseChild.getStringAttribute("value");
      String resultMap = caseChild.getStringAttribute("resultMap", processNestedResultMappings(caseChild, resultMappings));
      discriminatorMap.put(value, resultMap);
    }
    return builderAssistant.buildDiscriminator(resultType, column, javaTypeClass, jdbcTypeEnum, typeHandlerClass, discriminatorMap);
  }

  private void sqlElement(List<XNode> list) throws Exception {
    if (configuration.getDatabaseId() != null) {
      sqlElement(list, configuration.getDatabaseId());
    }
    sqlElement(list, null);
  }


  /**
   * 关键逻辑：databaseIdMatchesCurrent 保证同一 ID 的 SQL 片段，优先加载匹配当前数据库的版本；
   * 注意：此处仅存储 <sql> 节点，不解析内容，内容解析在 XMLIncludeTransformer 替换 <include> 时完成。
   * @param list
   * @param requiredDatabaseId
   * @throws Exception
   */
  private void sqlElement(List<XNode> list, String requiredDatabaseId) throws Exception {
    for (XNode context : list) {
      // 数据库方言
      String databaseId = context.getStringAttribute("databaseId");
      // SQL片段ID
      String id = context.getStringAttribute("id");
      // 拼接命名空间（如com.mybatis.UserMapper.user_columns）
      // 匹配databaseId，仅加载符合当前数据库的SQL片段
      id = builderAssistant.applyCurrentNamespace(id, false);
      if (databaseIdMatchesCurrent(id, databaseId, requiredDatabaseId)) {
        // 将SQL片段存入缓存
        sqlFragments.put(id, context);
      }
    }
  }
  
  private boolean databaseIdMatchesCurrent(String id, String databaseId, String requiredDatabaseId) {
    if (requiredDatabaseId != null) {
      if (!requiredDatabaseId.equals(databaseId)) {
        return false;
      }
    } else {
      if (databaseId != null) {
        return false;
      }
      // skip this fragment if there is a previous one with a not null databaseId
      //如果有重名的id了
      //<sql id="userColumns"> id,username,password </sql>
      if (this.sqlFragments.containsKey(id)) {
        XNode context = this.sqlFragments.get(id);
        //如果之前那个重名的sql id有databaseId，则false，否则难道true？这样新的sql覆盖老的sql？？？
        if (context.getStringAttribute("databaseId") != null) {
          return false;
        }
      }
    }
    return true;
  }

  /**
   * 是 XMLMapperBuilder 中构建单个结果映射规则（ResultMapping）的核心工具方法——
   * 它会解析 <id>/<result>/<idArg>/<arg>/<association>/<collection> 等所有结果映射节点的配置，
   * 提取完整的映射参数，最终通过 MapperBuilderAssistant 构建标准化的 ResultMapping 对象。
   *
   * 解析任意结果映射节点（<id>/<result>/<association> 等）的所有属性，
   * 转换为 MyBatis 内部的 ResultMapping 对象（该对象是结果集映射的最小单元）
   *
   * 解析 <id>/<result> 节点时直接调用；
   * 解析 <constructor> 下的 <idArg>/<arg> 时通过 processConstructorElement() 间接调用；
   * 解析 <association>/<collection> 等嵌套映射时也会调用；
   * @param context
   * @param resultType
   * @param flags
   * @return
   * @throws Exception
   */
  private ResultMapping buildResultMappingFromContext(XNode context, Class<?> resultType, List<ResultFlag> flags) throws Exception {
    String property = context.getStringAttribute("property");
    String column = context.getStringAttribute("column");
    String javaType = context.getStringAttribute("javaType");
    String jdbcType = context.getStringAttribute("jdbcType");
    // 用于处理嵌套查询
    /**
     * 用于嵌套查询，MyBatis 会先查用户，再通过 role_id 调用 getRoleById 查询角色，nestedSelect 存储该 SQL ID。
     * <association property="role" column="role_id" select="getRoleById"/>
     */
    String nestedSelect = context.getStringAttribute("select");

    /**
     * 嵌套结果映射
     * 关键属性说明：
     * notNullColumn：仅当指定列的值非空时，才执行该字段的映射（如 notNullColumn="role_id"，角色 ID 为空则不映射 role 属性）；
     * columnPrefix：嵌套结果映射时的列前缀（如 columnPrefix="role_"，则 role_id 映射为角色的 id 属性）。
     * typeHandler：自定义类型处理器（如将数据库的 VARCHAR 类型转换为 Java 的 Enum 类型）；
     * resultSet：存储过程返回多个结果集时，指定该映射对应的结果集名称；
     * foreignColumn：关联查询的外键列（如角色表的 user_id）。
     */
    String nestedResultMap = context.getStringAttribute("resultMap",
        processNestedResultMappings(context, Collections.<ResultMapping> emptyList()));
    String notNullColumn = context.getStringAttribute("notNullColumn");
    String columnPrefix = context.getStringAttribute("columnPrefix");
    String typeHandler = context.getStringAttribute("typeHandler");
    String resulSet = context.getStringAttribute("resultSet");
    String foreignColumn = context.getStringAttribute("foreignColumn");
    /**
     * 解析延迟加载属性（懒加载）
     * 优先取节点的 fetchType 属性（lazy/eager）；
     * 未配置则使用全局配置（configuration.isLazyLoadingEnabled()）；
     * lazy=true：延迟加载（嵌套对象如 role 只有在调用 user.getRole() 时才会查询）；
     * lazy=false：立即加载（查询用户时同时加载 role）。
     *
     * <resultMap id="UserWithRolesResultMap" type="com.mybatis.entity.User">
     *     <id property="id" column="user_id"/>
     *     <result property="username" column="user_name"/>
     *     <!-- 嵌套 ResultMap：一对多关联角色 -->
     *     <collection
     *         property="roles"         <!-- User 对象的 roles 集合属性 -->
     *         resultMap="RoleResultMap"
     *         columnPrefix="role_"/>
     *         这里的columPrefix用于指定嵌套查询中的字段前缀
     *
     *  <resultMap id="UserWithRoleResultMap" type="com.mybatis.entity.User">
     *     <id property="id" column="user_id"/>
     *     <result property="username" column="user_name"/>
     *     <!-- 嵌套RoleResultMap，添加columnPrefix="role_" -->
     *     <association
     *         property="role"
     *         resultMap="RoleResultMap"
     *         columnPrefix="role_"/> <!-- 关键配置：为嵌套列名加前缀 -->
     * </association>
     *
     *  <select id="getUserWithRole" resultMap="UserWithRoleResultMap">
     *     SELECT
     *         u.id AS user_id,          <!-- 外层User的列 -->
     *         u.username AS user_name,
     *         r.id AS role_id,          <!-- 嵌套Role的列：前缀role_ -->
     *         r.role_name AS role_name  <!-- 嵌套Role的列：前缀role_ -->
     *     FROM user u
     *     LEFT JOIN role r ON u.role_id = r.id
     *     WHERE u.id = #{id}
     * </select>
     * </resultMap>
     */
    boolean lazy = "lazy".equals(context.getStringAttribute("fetchType", configuration.isLazyLoadingEnabled() ? "lazy" : "eager"));
    Class<?> javaTypeClass = resolveClass(javaType);
    @SuppressWarnings("unchecked")
    Class<? extends TypeHandler<?>> typeHandlerClass = (Class<? extends TypeHandler<?>>) resolveClass(typeHandler);
    JdbcType jdbcTypeEnum = resolveJdbcType(jdbcType);
    return builderAssistant.buildResultMapping(resultType, property, column, javaTypeClass, jdbcTypeEnum, nestedSelect, nestedResultMap, notNullColumn, columnPrefix, typeHandlerClass, flags, resulSet, foreignColumn, lazy);
  }
  
  //5.1.1.1 处理嵌套的result map
  private String processNestedResultMappings(XNode context, List<ResultMapping> resultMappings) throws Exception {
	  //处理association|collection|case
    if ("association".equals(context.getName())
        || "collection".equals(context.getName())
        || "case".equals(context.getName())) {
    	
//    	<resultMap id="blogResult" type="Blog">
//    	  <association property="author" column="author_id" javaType="Author" select="selectAuthor"/>
//    	</resultMap>
//如果不是嵌套查询
      if (context.getStringAttribute("select") == null) {
    	//则递归调用5.1 resultMapElement
        ResultMap resultMap = resultMapElement(context, resultMappings);
        return resultMap.getId();
      }
    }
    return null;
  }

  private void bindMapperForNamespace() {
    String namespace = builderAssistant.getCurrentNamespace();
    if (namespace != null) {
      Class<?> boundType = null;
      try {
        boundType = Resources.classForName(namespace);
      } catch (ClassNotFoundException e) {
        //ignore, bound type is not required
      }
      if (boundType != null) {
        if (!configuration.hasMapper(boundType)) {
          // Spring may not know the real resource name so we set a flag
          // to prevent loading again this resource from the mapper interface
          // look at MapperAnnotationBuilder#loadXmlResource
          configuration.addLoadedResource("namespace:" + namespace);
          configuration.addMapper(boundType);
        }
      }
    }
  }

}
