/*
 * The MIT License (MIT)
 *
 * Copyright (c) 2018 Spencer Park
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package io.github.spencerpark.ijava.runtime;


import java.util.ArrayList;
import java.util.List;

public class Interpolation {

    private String value;
    private int index;

    public static List<Expression> parse(String value) {
        return new Interpolation(value).parse();
    }

    private Interpolation(String stringValue) {
        value = stringValue;
    }

    private List<Expression> parse() {
        List<Expression> list = new ArrayList<>();
        StringBuilder sb = new StringBuilder();
        int length = value.length();
        int offset = 0;
        for (index = 0; index < length; ++index) {
            char c = value.charAt(index);
            if (c == '$') {
                Expression expr = parseExpr();
                if (expr != null) {
                    if (sb.length() > 0) {
                        list.add(new Expression(sb.toString(), true));
                        sb.setLength(0);
                    }
                    list.add(expr);
                    continue;
                }
            }
            sb.append(c);
        }

        if (list.isEmpty() || sb.length() > 0) {
            list.add(new Expression(sb.toString(), true));
        }
        return list;
    }

    private Expression parseExpr() {
        if (index + 1 == value.length()) {
            return null;
        }

        if (value.charAt(index + 1) == '{') {
            return parseBraceExpr();
        }
        return parseSimpleExpr();
    }

    private Expression parseBraceExpr() {
        int length = value.length();
        StringBuilder sb = new StringBuilder();
        for (int pos = index + 2; pos < length; ++pos) {
            char c = value.charAt(pos);
            if (c != '}') {
                sb.append(c);
            } else {
                if (sb.length() > 0) {
                    index = pos;
                    return new Expression(sb.toString(), false);
                }
                break;
            }
        }
        return null;
    }

    private Expression parseSimpleExpr() {
        int length = value.length();
        StringBuilder sb = new StringBuilder();
        for (int pos = index + 1; pos < length; pos++) {
            char c = value.charAt(pos);
            if (sb.length() == 0) {
                if (c != '$' && Character.isJavaIdentifierStart(c)) {
                    sb.append(c);
                } else {
                    return null;
                }
            } else if (c != '$' && Character.isJavaIdentifierPart(c)) {
                sb.append(c);
            } else {
                break;
            }
            index = pos;
        }
        return sb.length() > 0 ? new Expression(sb.toString(), false) : null;
    }

    public static final class Expression {

        private String expression;
        private boolean constant;

        Expression(String expression, boolean constant) {
            this.expression = expression;
            this.constant = constant;
        }

        public String getExpression() {
            return expression;
        }

        public boolean isConstant() {
            return constant;
        }
    }
}
