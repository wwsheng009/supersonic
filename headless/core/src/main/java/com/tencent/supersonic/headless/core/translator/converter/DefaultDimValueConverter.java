package com.tencent.supersonic.headless.core.translator.converter;

import com.google.common.collect.Lists;
import com.tencent.supersonic.common.jsqlparser.SqlAddHelper;
import com.tencent.supersonic.common.jsqlparser.SqlSelectHelper;
import com.tencent.supersonic.common.pojo.enums.TimeDimensionEnum;
import com.tencent.supersonic.headless.core.pojo.QueryStatement;
import com.tencent.supersonic.headless.core.translator.parser.s2sql.Dimension;
import lombok.extern.slf4j.Slf4j;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.StringValue;
import net.sf.jsqlparser.expression.operators.relational.ExpressionList;
import net.sf.jsqlparser.expression.operators.relational.InExpression;
import net.sf.jsqlparser.schema.Column;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Slf4j
@Component("DefaultDimValueConverter")
public class DefaultDimValueConverter implements QueryConverter {

    @Override
    public boolean accept(QueryStatement queryStatement) {
        return Objects.nonNull(queryStatement.getSqlQueryParam())
                && StringUtils.isNotBlank(queryStatement.getSqlQueryParam().getSql());
    }

    @Override
    public void convert(QueryStatement queryStatement) {
        List<Dimension> dimensions = queryStatement.getOntology().getDimensions().stream()
                .filter(dimension -> !CollectionUtils.isEmpty(dimension.getDefaultValues()))
                .collect(Collectors.toList());
        if (CollectionUtils.isEmpty(dimensions)) {
            return;
        }
        String sql = queryStatement.getSqlQueryParam().getSql();
        List<String> whereFields = SqlSelectHelper.getWhereFields(sql).stream()
                .filter(field -> !TimeDimensionEnum.containsTimeDimension(field))
                .collect(Collectors.toList());
        if (!CollectionUtils.isEmpty(whereFields)) {
            return;
        }
        List<Expression> expressions = Lists.newArrayList();
        for (Dimension dimension : dimensions) {
            ExpressionList expressionList = new ExpressionList();
            List<Expression> exprs = new ArrayList<>();
            dimension.getDefaultValues().forEach(value -> exprs.add(new StringValue(value)));
            expressionList.setExpressions(exprs);
            InExpression inExpression = new InExpression();
            inExpression.setLeftExpression(new Column(dimension.getBizName()));
            inExpression.setRightExpression(expressionList);
            expressions.add(inExpression);
            if (Objects.nonNull(queryStatement.getSqlQueryParam().getTable())) {
                queryStatement.getOntologyQueryParam().getDimensions().add(dimension.getBizName());
            }
        }
        sql = SqlAddHelper.addWhere(sql, expressions);
        queryStatement.getSqlQueryParam().setSql(sql);
    }
}
