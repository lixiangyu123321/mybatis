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
package org.apache.ibatis.binding;

import org.apache.ibatis.annotations.MapKey;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.mapping.MappedStatement;
import org.apache.ibatis.mapping.SqlCommandType;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;
import org.apache.ibatis.session.SqlSession;

import java.lang.reflect.Array;
import java.lang.reflect.Method;
import java.util.*;

/**
 * **** 连接Mapper接口方法与底层SQL执行的核心桥梁类 ****
 * **** 封装Mapper接口方法的SQL命令信息和方法签名信息 ****
 * 负责将 Mapper 方法的调用转换为 SqlSession 的具体操作，并处理参数绑定、返回值转换、异常抛出等核心逻辑
 * <p>
 * 核心设计目标是解耦 Mapper 接口的抽象方法与实际的 SQL 执行逻辑，
 * 让开发者只需定义接口方法和 SQL 映射，无需关心底层的执行细节。
 * 以下从类的核心定位、核心内部类、核心执行逻辑、关键辅助方法和设计细节与异常场景五个维度进行深度解析。
 * </p>
 *
 * @author Clinton Begin
 * @author Eduardo Macarron
 * @author Lasse Voss
 */

/**
 * 映射器方法
 */
public class MapperMethod {

    /**
     * 封装SQL命令名称以及类型
     */
    private final SqlCommand command;
    /**
     * 封装对应方法的签名信息（返回类型，参数，RowBounds，ResultHandler）
     */
    private final MethodSignature method;

    public MapperMethod(Class<?> mapperInterface, Method method, Configuration config) {
        this.command = new SqlCommand(config, mapperInterface, method);
        this.method = new MethodSignature(config, method);
    }

    /**
     * 根据SQL命令类型分发SQL命令与参数到SqlSession中执行
     * @param sqlSession
     * @param args
     * @return
     */
    public Object execute(SqlSession sqlSession, Object[] args) {
        Object result;
        // 基于方法类型调用不同的SqlSession的方法
        if (SqlCommandType.INSERT == command.getType()) {
            Object param = method.convertArgsToSqlCommandParam(args);
            result = rowCountResult(sqlSession.insert(command.getName(), param));
        } else if (SqlCommandType.UPDATE == command.getType()) {
            Object param = method.convertArgsToSqlCommandParam(args);
            result = rowCountResult(sqlSession.update(command.getName(), param));
        } else if (SqlCommandType.DELETE == command.getType()) {
            Object param = method.convertArgsToSqlCommandParam(args);
            result = rowCountResult(sqlSession.delete(command.getName(), param));
        } else if (SqlCommandType.SELECT == command.getType()) {
            if (method.returnsVoid() && method.hasResultHandler()) {
                // 返回void 但是 定义了结果集处理器
                // 用于自定义对大量数据的处理，可能是写入文件或者CVS，所以返回void
                executeWithResultHandler(sqlSession, args);
                result = null;
            } else if (method.returnsMany()) {
                // 返回集合/数组
                result = executeForMany(sqlSession, args);
            } else if (method.returnsMap()) {
                // 返回Map
                result = executeForMap(sqlSession, args);
            } else {
                // 返回一条记录
                Object param = method.convertArgsToSqlCommandParam(args);
                result = sqlSession.selectOne(command.getName(), param);
            }
        } else {
            throw new BindingException("Unknown execution method for: " + command.getName());
        }
        if (result == null && method.getReturnType().isPrimitive() && !method.returnsVoid()) {
            throw new BindingException("Mapper method '" + command.getName()
                    + " attempted to return null from a method with a primitive return type (" + method.getReturnType() + ").");
        }
        return result;
    }

    /**
     * 等于说增删改查操作的对应类型的适配是在这里做的
     * 将数据库增删改操作返回的受影响行数（int 类型），转换为 Mapper 接口方法声明的返回类型（如 void、int、long、boolean）
     * @param rowCount
     * @return
     */
    private Object rowCountResult(int rowCount) {
        final Object result;
        if (method.returnsVoid()) {
            result = null;
        } else if (Integer.class.equals(method.getReturnType()) || Integer.TYPE.equals(method.getReturnType())) {
            result = Integer.valueOf(rowCount);
        } else if (Long.class.equals(method.getReturnType()) || Long.TYPE.equals(method.getReturnType())) {
            result = Long.valueOf(rowCount);
        } else if (Boolean.class.equals(method.getReturnType()) || Boolean.TYPE.equals(method.getReturnType())) {
            result = Boolean.valueOf(rowCount > 0);
        } else {
            throw new BindingException("Mapper method '" + command.getName() + "' has an unsupported return type: " + method.getReturnType());
        }
        return result;
    }

    /**
     * <p>该方法的设计目标是解耦结果集的加载与处理逻辑，</p>
     * 让开发者通过自定义 ResultHandler 实现对查询结果的逐行自定义处理（如逐行写入文件、逐行入库等）。
     * 在 MyBatis 中，常规的 SELECT 查询会将结果集一次性封装为 List/Map/ 单个对象返回，
     * 但面对百万级 / 千万级大数据量查询时，这种方式会导致 JVM 堆内存溢出。
     * 而 ResultHandler 提供了逐行处理结果集的能力：MyBatis 会在解析 ResultSet 的每一行数据后，
     * 调用 ResultHandler.handleResult() 方法，开发者可在该方法中实现自定义逻辑（如逐行写入 CSV 文件、逐行插入另一张表）。
     * @param sqlSession
     * @param args
     */
    private void executeWithResultHandler(SqlSession sqlSession, Object[] args) {
        MappedStatement ms = sqlSession.getConfiguration().getMappedStatement(command.getName());
        if (void.class.equals(ms.getResultMaps().get(0).getType())) {
            throw new BindingException("method " + command.getName()
                    + " needs either a @ResultMap annotation, a @ResultType annotation,"
                    + " or a resultType attribute in XML so a ResultHandler can be used as a parameter.");
        }
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            sqlSession.select(command.getName(), param, rowBounds, method.extractResultHandler(args));
        } else {
            sqlSession.select(command.getName(), param, method.extractResultHandler(args));
        }
    }

    /**
     * 返回List 或者 Array
     * @param sqlSession
     * @param args
     * @return
     * @param <E>
     */
    private <E> Object executeForMany(SqlSession sqlSession, Object[] args) {
        List<E> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        // 判断分页情况
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.<E>selectList(command.getName(), param, rowBounds);
        } else {
            result = sqlSession.<E>selectList(command.getName(), param);
        }
        // 先转为List， 后转为Array类型
        if (!method.getReturnType().isAssignableFrom(result.getClass())) {
            if (method.getReturnType().isArray()) {
                return convertToArray(result);
            } else {
                return convertToDeclaredCollection(sqlSession.getConfiguration(), result);
            }
        }
        return result;
    }

    /**
     * 将 MyBatis 底层查询默认返回的 List 集合，
     * 转换为 Mapper 接口方法声明的自定义集合类型（如 Set、HashSet、LinkedList、SortedSet 等）
     * @param config
     * @param list
     * @return
     * @param <E>
     */
    private <E> Object convertToDeclaredCollection(Configuration config, List<E> list) {
        Object collection = config.getObjectFactory().create(method.getReturnType());
        MetaObject metaObject = config.newMetaObject(collection);
        metaObject.addAll(list);
        return collection;
    }

    /**
     * 将列表转换为数组
     *
     * @param list 要转换的列表
     * @return 转换后的数组
     */
    @SuppressWarnings("unchecked")
    private <E> E[] convertToArray(List<E> list) {
        E[] array = (E[]) Array.newInstance(method.getReturnType().getComponentType(), list.size());
        array = list.toArray(array);
        return array;
    }

    /**
     * 执行SQL查询并返回结果映射
     *
     * @param sqlSession SQL会话对象
     * @param args 方法参数数组
     * @return 查询结果的映射，键值对类型由泛型 K 和 V 指定
     */
    private <K, V> Map<K, V> executeForMap(SqlSession sqlSession, Object[] args) {
        Map<K, V> result;
        Object param = method.convertArgsToSqlCommandParam(args);
        if (method.hasRowBounds()) {
            RowBounds rowBounds = method.extractRowBounds(args);
            result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey(), rowBounds);
        } else {
            result = sqlSession.<K, V>selectMap(command.getName(), param, method.getMapKey());
        }
        return result;
    }

    /**
     * @desc: 参数映射表，用于存储键值对
     */
    public static class ParamMap<V> extends HashMap<String, V> {

        private static final long serialVersionUID = -2212268410512043556L;

        @Override
        public V get(Object key) {
            if (!super.containsKey(key)) {
                throw new BindingException("Parameter '" + key + "' not found. Available parameters are " + keySet());
            }
            return super.get(key);
        }

    }

    /**
     * 封装SQL命令与类型
     * 在实际开发中，Mapper 接口可以继承其他接口，父接口中会声明通用的 CRUD 方法，
     * 子接口直接继承使用，无需重复定义。此时如果只根据子接口的全限定名拼接 statementName，
     * 会找不到对应的 MappedStatement（因为 SQL 映射是绑定到父接口的方法上的），从而抛出「Invalid bound statement」异常。
     */
    public static class SqlCommand {

        // 核心成员变量1：MappedStatement的唯一标识（格式：Mapper接口全限定名.方法名）
        private final String name;
        // 核心成员变量2：SQL命令类型（INSERT/UPDATE/DELETE/SELECT/UNKNOWN）
        private final SqlCommandType type;

        // 构造方法：初始化SQL命令，绑定Mapper方法与MappedStatement
        // 参数说明：
        // configuration：MyBatis的全局配置核心类，存储所有MappedStatement、映射规则等
        // mapperInterface：当前Mapper接口的Class对象（如UserMapper.class）
        // method：当前Mapper接口中的方法对象（如UserMapper的selectUserById方法）
        public SqlCommand(Configuration configuration, Class<?> mapperInterface, Method method) {
            // 1. 拼接当前Mapper接口的MappedStatement默认ID：接口全限定名 + 方法名
            // 例如：com.mapper.UserMapper + .selectUserById → com.mapper.UserMapper.selectUserById
            String statementName = mapperInterface.getName() + "." + method.getName();
            // 2. 初始化MappedStatement引用为null，后续从配置中查找
            MappedStatement ms = null;
            // 3. 先从全局配置中查找当前拼接的statementName对应的MappedStatement
            if (configuration.hasStatement(statementName)) {
                // 3.1 若存在，直接获取该MappedStatement（核心的SQL映射配置对象）
                ms = configuration.getMappedStatement(statementName);
            } else if (!mapperInterface.equals(method.getDeclaringClass().getName())) { // issue #35
                // 4. 若当前接口中未找到，处理【Mapper接口继承】的场景（issue #35是MyBatis的issue编号，修复接口继承导致的绑定问题）
                //    判定条件：当前传入的mapperInterface的类名 不等于 方法实际声明的类名（说明方法来自父接口）
                // 4.1 拼接父接口的MappedStatement ID：方法声明类的全限定名 + 方法名
                String parentStatementName = method.getDeclaringClass().getName() + "." + method.getName();
                // 4.2 从全局配置中查找父接口的MappedStatement
                if (configuration.hasStatement(parentStatementName)) {
                    // 4.3 若存在，获取父接口的MappedStatement
                    ms = configuration.getMappedStatement(parentStatementName);
                }
            }
            // 5. 最终未找到对应的MappedStatement，抛出经典的绑定异常
            //    异常信息：Invalid bound statement (not found) 是MyBatis开发中最常见的异常之一
            if (ms == null) {
                throw new BindingException("Invalid bound statement (not found): " + statementName);
            }
            // 6. 初始化成员变量：赋值MappedStatement的唯一ID（与statementName一致或父接口的ID）
            name = ms.getId();
            // 7. 初始化成员变量：赋值SQL命令类型（INSERT/UPDATE/DELETE/SELECT）
            type = ms.getSqlCommandType();
            // 8. 校验命令类型：若为UNKNOWN（未定义），抛出绑定异常
            if (type == SqlCommandType.UNKNOWN) {
                throw new BindingException("Unknown execution method for: " + name);
            }
        }

        // 获取MappedStatement的唯一标识
        public String getName() {
            return name;
        }

        // 获取SQL命令类型（INSERT/UPDATE/DELETE/SELECT）
        public SqlCommandType getType() {
            return type;
        }
    }

    /**
     * 封装Mapper接口的相应方法
     */
    public static class MethodSignature {
        /**
         * 方法是否返回集合/数组类型
         */
        private final boolean returnsMany;
        /**
         * 方法是否返回Map类型
         */
        private final boolean returnsMap;
        /**
         * 方法是否返回void
         */
        private final boolean returnsVoid;
        /**
         * 方法是否返回Class对象
         */
        private final Class<?> returnType;
        /**
         * 返回Map时，@MapKey注解指定的键名
         */
        private final String mapKey;
        /**
         * ResultHandler参数在方法参数列表中的索引，无则为null
         */
        private final Integer resultHandlerIndex;
        /**
         * RowBounds参数在方法参数列表中的索引，无则为null
         */
        private final Integer rowBoundsIndex;
        /**
         * 方法参数的索引与名称映射，（排除RowBounds/ResultHandler），SortedMap保证按参数顺序排序
         */
        private final SortedMap<Integer, String> params;
        /**
         * 是否使用@Param注解指定了命名参数（别名）
         */
        private final boolean hasNamedParameters;

        /**
         * 解析Mapper方法的签名信息，初始化所有成员变量
         * @param configuration
         * @param method
         */
        public MethodSignature(Configuration configuration, Method method) {
            this.returnType = method.getReturnType();
            this.returnsVoid = void.class.equals(this.returnType);
            this.returnsMany = (configuration.getObjectFactory().isCollection(this.returnType) || this.returnType.isArray());
            this.mapKey = getMapKey(method);
            this.returnsMap = (this.mapKey != null);
            this.hasNamedParameters = hasNamedParams(method);
            this.rowBoundsIndex = getUniqueParamIndex(method, RowBounds.class);
            this.resultHandlerIndex = getUniqueParamIndex(method, ResultHandler.class);
            this.params = Collections.unmodifiableSortedMap(getParams(method, this.hasNamedParameters));
        }

        /**
         * 将Mapper方法的入参数组转换为SQL执行的参数对象
         * @param args 对应Mapper方法的实际参数值，注意是“值”， params是参数类型
         * @return
         */
        public Object convertArgsToSqlCommandParam(Object[] args) {
            final int paramCount = params.size();
            // 无参
            if (args == null || paramCount == 0) {
                return null;
            } else if (!hasNamedParameters && paramCount == 1) {
                return args[params.keySet().iterator().next().intValue()];
            } else {
                final Map<String, Object> param = new ParamMap<Object>();
                int i = 0;
                for (Map.Entry<Integer, String> entry : params.entrySet()) {
                    // 对应@Param中定义的别名，值对应入参
                    param.put(entry.getValue(), args[entry.getKey().intValue()]);
                    // 多参数，且没有使用@param注解，默认键为param为“param” + 索引下标，值为对应入参
                    final String genericParamName = "param" + String.valueOf(i + 1);
                    if (!param.containsKey(genericParamName)) {
                        param.put(genericParamName, args[entry.getKey()]);
                    }
                    i++;
                }
                return param;
            }
        }

        public boolean hasRowBounds() {
            return rowBoundsIndex != null;
        }

        public RowBounds extractRowBounds(Object[] args) {
            return hasRowBounds() ? (RowBounds) args[rowBoundsIndex] : null;
        }

        public boolean hasResultHandler() {
            return resultHandlerIndex != null;
        }

        public ResultHandler extractResultHandler(Object[] args) {
            return hasResultHandler() ? (ResultHandler) args[resultHandlerIndex] : null;
        }

        public String getMapKey() {
            return mapKey;
        }

        public Class<?> getReturnType() {
            return returnType;
        }

        public boolean returnsMany() {
            return returnsMany;
        }

        public boolean returnsMap() {
            return returnsMap;
        }

        public boolean returnsVoid() {
            return returnsVoid;
        }

        /**
         * 查找指定类型的参数在方法参数列表中的唯一索引
         * @param method
         * @param paramType
         * @return
         */
        private Integer getUniqueParamIndex(Method method, Class<?> paramType) {
            Integer index = null;
            final Class<?>[] argTypes = method.getParameterTypes();
            for (int i = 0; i < argTypes.length; i++) {
                if (paramType.isAssignableFrom(argTypes[i])) {
                    if (index == null) {
                        index = i;
                    } else {
                        throw new BindingException(method.getName() + " cannot have multiple " + paramType.getSimpleName() + " parameters");
                    }
                }
            }
            return index;
        }

        /**
         * 从方法上获得对应@MapKey注解的值
         * @param method
         * @return
         */
        private String getMapKey(Method method) {
            String mapKey = null;
            if (Map.class.isAssignableFrom(method.getReturnType())) {
                final MapKey mapKeyAnnotation = method.getAnnotation(MapKey.class);
                if (mapKeyAnnotation != null) {
                    mapKey = mapKeyAnnotation.value();
                }
            }
            return mapKey;
        }

        /**
         * 解析方法的所有有效参数，排除RowBounds/ResultHandler
         * @param method
         * @param hasNamedParameters
         * @return
         */
        private SortedMap<Integer, String> getParams(Method method, boolean hasNamedParameters) {
            //用一个TreeMap,这样就保证还是按参数的先后顺序
            final SortedMap<Integer, String> params = new TreeMap<Integer, String>();
            final Class<?>[] argTypes = method.getParameterTypes();
            for (int i = 0; i < argTypes.length; i++) {
                //是否不是RowBounds/ResultHandler类型的参数
                if (!RowBounds.class.isAssignableFrom(argTypes[i]) && !ResultHandler.class.isAssignableFrom(argTypes[i])) {
                    //参数名字默认为0,1,2，这就是为什么xml里面可以用#{1}这样的写法来表示参数了
                    String paramName = String.valueOf(params.size());
                    if (hasNamedParameters) {
                        //还可以用注解@Param来重命名参数
                        paramName = getParamNameFromAnnotation(method, i, paramName);
                    }
                    params.put(i, paramName);
                }
            }
            return params;
        }

        /**
         * 从指定索引的参数上获得@Param注解的值
         * @param method
         * @param i
         * @param paramName
         * @return
         */
        private String getParamNameFromAnnotation(Method method, int i, String paramName) {
            final Object[] paramAnnos = method.getParameterAnnotations()[i];
            for (Object paramAnno : paramAnnos) {
                if (paramAnno instanceof Param) {
                    paramName = ((Param) paramAnno).value();
                }
            }
            return paramName;
        }

        /**
         * 判断方法是否使用了@Param注解指定了命名参数
         * @param method
         * @return
         */
        private boolean hasNamedParams(Method method) {
            boolean hasNamedParams = false;
            final Object[][] paramAnnos = method.getParameterAnnotations();
            for (Object[] paramAnno : paramAnnos) {
                for (Object aParamAnno : paramAnno) {
                    if (aParamAnno instanceof Param) {
                        //查找@Param注解,一般不会用注解吧，可以忽略
                        hasNamedParams = true;
                        break;
                    }
                }
            }
            return hasNamedParams;
        }

    }

}
