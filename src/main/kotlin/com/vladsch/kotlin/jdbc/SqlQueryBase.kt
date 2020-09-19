package com.vladsch.kotlin.jdbc

import org.jetbrains.annotations.TestOnly
import java.sql.PreparedStatement

abstract class SqlQueryBase<T : SqlQueryBase<T>>(
    val statement: String,
    params: List<Any?> = listOf(),
    namedParams: Map<String, Any?> = mapOf()
) {
    protected val params = ArrayList(params.asParamList())
    protected val namedParams: HashMap<String, Parameter<*>> = HashMap(namedParams.asParamMap())

    private var _queryDetails: Details? = null
    private val queryDetails: Details get() = finalizedQuery()

    val replacementMap get() = queryDetails.replacementMap
    val cleanStatement get() = queryDetails.cleanStatement

    val inputParams: Map<String, Parameter<*>> get() = this.namedParams.filter { it.value.inOut.isIn }
    val outputParams: Map<String, Parameter<*>> get() = this.namedParams.filter { it.value.inOut.isOut }

    data class Details(
        val listParamsMap: Map<String, String>,
        val replacementMap: Map<String, List<Int>>,
        val cleanStatement: String,
        val paramCount: Int
    )

    protected fun resetDetails() {
        _queryDetails = null
    }

    private fun finalizedQuery(): Details {
        if (_queryDetails == null) {
            // called when parameters are defined and have request for clean statement or populate params
            val listParamsMap: HashMap<String, String> = HashMap()
            var idxOffset = 0
            val findAll = regex.findAll(statement)
            val replacementMap = findAll.filter { group ->
                if (!group.value.startsWith(":")) {
                    // not a parameter
                    false
                } else {
                    // filter out commented lines
                    val pos = statement.lastIndexOf('\n', group.range.first)
                    val lineStart = if (pos == -1) 0 else pos + 1;
                    !regexSqlComment.containsMatchIn(statement.subSequence(lineStart, statement.length))
                }
            }.map { group ->
                val paramName = group.value.substring(1);
                val paramValue = namedParams[paramName]
                val pair = Pair(paramName, idxOffset)

                if (paramValue?.value is Collection<*>) {
                    val size = paramValue.value.size
                    listParamsMap[paramName] = "?,".repeat(size).substring(0, size * 2 - 1)
                    idxOffset += size - 1
                }

                idxOffset++
                pair
            }.groupBy({ it.first }, { it.second })

            val cleanStatement = regex.replace(statement) { matchResult ->
                if (!matchResult.value.startsWith(":")) {
                    // not a parameter, leave as is
                    matchResult.value
                } else {
                    val paramName = matchResult.value.substring(1);
                    listParamsMap[paramName] ?: "?"
                }
            }
            _queryDetails = Details(listParamsMap, replacementMap, cleanStatement, idxOffset)
        }
        return _queryDetails!!
    }

    fun populateParams(stmt: PreparedStatement) {
        // TODO: handle mix of ? and named params, by computing indices for ? params and populating based on index
        if (replacementMap.isNotEmpty()) {
            populateNamedParams(stmt)
        } else {
            params.forEachIndexed { index, value ->
                stmt.setTypedParam(index + 1, value.param())
            }
        }
    }

    protected fun forEachNamedParam(inOut: InOut, action: (paramName: String, param: Parameter<*>, occurrences: List<Int>) -> Unit) {
        replacementMap.forEach { (paramName, occurrences) ->
            val param = namedParams[paramName] ?: NULL_PARAMETER
            if (param.inOut.isOf(inOut)) action.invoke(paramName, param, occurrences)
        }
    }

    protected open fun populateNamedParams(stmt: PreparedStatement) {
        forEachNamedParam(InOut.IN) { _, param, occurrences ->
            if (param.value is Collection<*>) {
                param.value.forEachIndexed { idx, paramItem ->
                    occurrences.forEach {
                        stmt.setTypedParam(it + idx + 1, paramItem, param)
                    }
                }
            } else {
                occurrences.forEach {
                    stmt.setTypedParam(it + 1, param)
                }
            }
        }
    }

    @TestOnly
    fun getParams(): List<Any?> {
        return if (replacementMap.isNotEmpty()) {
            val sqlParams = ArrayList<Any?>(queryDetails.paramCount)

            for (i in 0 until queryDetails.paramCount) {
                sqlParams.add(null)
            }

            forEachNamedParam(InOut.IN) { _, param, occurrences ->
                occurrences.forEach {
                    if (param.value is Collection<*>) {
                        param.value.forEachIndexed { idx, paramItem ->
                            sqlParams[it + idx] = paramItem
                        }
                    } else {
                        sqlParams[it] = param.value
                    }
                }
            }

            sqlParams
        } else {
            params
        }
    }

    fun params(vararg params: Any?): T {
        this.params.addAll(params.asParamList())
        this._queryDetails = null
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    fun paramsArray(params: Array<out Any?>): T {
        this.params.addAll(params.asParamList())
        this._queryDetails = null
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    fun paramsList(params: Collection<Any?>): T {
        this.params.addAll(params.asParamList())
        this._queryDetails = null
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    fun params(params: Map<String, Any?>): T {
        namedParams.putAll(params.asParamMap())
        this._queryDetails = null
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    fun params(vararg params: Pair<String, Any?>): T {
        namedParams.putAll(params.asParamMap())
        this._queryDetails = null
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    fun paramsArray(params: Array<out Pair<String, Any?>>): T {
        namedParams.putAll(params.asParamMap())
        this._queryDetails = null
        @Suppress("UNCHECKED_CAST")
        return this as T
    }

    @Deprecated(message = "Use directional parameter construction with to/inTo, outTo, inOutTo infix functions", replaceWith = ReplaceWith("params"))
    fun inParams(params: Map<String, Any?>): T {
        return params(params.asParamMap(InOut.IN))
    }

    @Deprecated(message = "Use directional parameter construction with to/inTo, outTo, inOutTo infix functions", replaceWith = ReplaceWith("params"))
    fun inParams(vararg params: Pair<String, Any?>): T {
        return params(params.asParamMap(InOut.IN))
    }

    @Deprecated(message = "Use directional parameter construction with to/inTo, outTo, inOutTo infix functions", replaceWith = ReplaceWith("paramsArray"))
    fun inParamsArray(params: Array<out Pair<String, Any?>>): T {
        return paramsArray(params)
    }

    override fun toString(): String {
        return "SqlQueryBase(statement='$statement', params=$params, namedParams=$namedParams, replacementMap=$replacementMap, cleanStatement='$cleanStatement')"
    }

    companion object {
        // must begin with
        private val NULL_PARAMETER = Parameter(null, Any::class.java, InOut.IN)
        private val regex = Regex(":\\w+|'(?:[^']|'')*'|`(?:[^`])*`|\"(?:[^\"])*\"")
        private val regexSqlComment = Regex("""^\s*(?:--\s|#)""")
    }
}
