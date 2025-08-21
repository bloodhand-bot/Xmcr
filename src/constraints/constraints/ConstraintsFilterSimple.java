package constraints.constraints;

import javafx.util.Pair;
import java.util.*;

/**
 * Simple constraints filter for optimizing constraint solving by checking
 * for conflicts in program order and causal constraints.
 * This version focuses purely on filtering without data extraction.
 */
public class ConstraintsFilterSimple {

    /**
     * Filter constraints to check for conflicts and optimize constraint solving.
     *
     * @param CONS_ASSERT_PO    Program order constraints
     * @param CONS_ASSERT_VALID Lock-related constraints
     * @param causalConstraint  New read-write constraints
     * @return Pair<Boolean, StringBuilder> where Boolean indicates if constraints are satisfiable,
     *         and StringBuilder contains the filtered constraint if available
     */
    public Pair<Boolean, StringBuilder> doFilter_with_expression(StringBuilder CONS_ASSERT_PO, StringBuilder CONS_ASSERT_VALID, StringBuilder causalConstraint) {
        try {
            StringBuilder result = new StringBuilder("");
            List<String[]> variables = new ArrayList<>();
            variables.addAll(findVariable(CONS_ASSERT_PO.toString()));
            variables.addAll(findVariable(CONS_ASSERT_VALID.toString()));

            // Build adjacency list from program order and lock constraints
            List<OrderNode> orderMapList = createOrderMap(variables);

            // Process causal constraints
            StringBuilder sb = new StringBuilder();
            String causalStr = causalConstraint.toString();

            // 处理命名约束格式 (! ... :named INTER_X)
            causalStr = causalStr.replaceAll("\\(\\s*!\\s+([^:]+)\\s*:named\\s+[^)]+\\)", "$1");

            for (char c : causalStr.toCharArray()) {
                if (c != '\n') {
                    if (c == '(' || c == ')') {
                        sb.append(" ");
                    } else {
                        sb.append(c);
                    }
                }
            }

            // Split causal constraint into individual assertions
            String[] asserts = sb.toString().trim().split("assert");
            List<String> assertsString = new ArrayList<>();
            for (String anAssert : asserts) {
                if (!anAssert.equals("\\s+") && !anAssert.isEmpty()) {
                    assertsString.add(anAssert);
                }
            }

            // Check each assertion for conflicts
            for (String anAssert : assertsString) {
                Queue<Pair<String, String>> rebuildCausalConstraint = rebuildExpression_with_expression(anAssert, orderMapList);

                if (!rebuildCausalConstraint.isEmpty()) {
                    Stack<Pair<String, String>> stack = new Stack<>();
                    while (!rebuildCausalConstraint.isEmpty()) {
                        Pair<String, String> var1 = rebuildCausalConstraint.poll();
                        while (!stack.isEmpty() && ("true".equals(stack.peek().getKey()) || "false".equals(stack.peek().getKey())) && ("true".equals(var1.getKey()) || "false".equals(var1.getKey()))) {
                            Pair<String, String> var2 = stack.pop();
                            Pair<String, String> option = stack.pop();

                            boolean var1Value = "true".equals(var1.getKey());
                            boolean var2Value = "true".equals(var2.getKey());
                            boolean finalValue;
                            String expression = "";

                            if ("and".equals(option.getKey())) {
                                finalValue = var1Value && var2Value;
                                if (finalValue) expression = "(and " + var1.getValue() + " " + var2.getValue() + " )";
                            } else {
                                finalValue = var1Value || var2Value;
                                if (finalValue) {
                                    if (var1Value && var2Value) expression = "(or " + var1.getValue() + " " + var2.getValue() + " )";
                                    else if (var1Value) expression = var1.getValue();
                                    else if (var2Value) expression = var2.getValue();
                                }
                            }
                            var1 = new Pair<>(String.valueOf(finalValue), expression);
                        }
                        stack.push(var1);
                    }

                    if ("false".equals(stack.get(0).getKey())) {
                        return new Pair<>(false, result);
                    } else {
                        String constraint = stack.get(0).getValue();
                        // 只有当约束内容不为空时才添加
                        if (constraint != null && !constraint.trim().isEmpty()) {
                            result.append("(assert " + constraint + ")\n");
                        }
                    }
                }
            }
            return new Pair<>(true, result);
        } catch (Exception e) {
            // If any error occurs, return original constraints as satisfiable
            return new Pair<>(true, causalConstraint);
        }
    }

    /**
     * Extract variable relationships from constraint string.
     * Returns a list of [var1, var2, operator] arrays.
     */
    public List<String[]> findVariable(String s) {
        List<String[]> result = new ArrayList<>();
        if (s.contains("or")) {
            // Skip constraints with 'or' for simplicity
            return result;
        }
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c != '\n') {
                sb.append(c);
                if (c == '(' || c == ')') {
                    sb.append(" ");
                }
            }
        }
        String[] s1 = sb.toString().split("\\s+");
        for (int i = 0; i < s1.length; i++) {
            String s2 = s1[i];
            if ("<".equals(s2) || ">".equals(s2)) {
                String var1 = s1[i + 1];
                String var2 = s1[i + 2];
                result.add(new String[]{var1, var2, s2});
                i += 2;
            }
        }
        return result;
    }

    /**
     * Rebuild expression with conflict checking against order map.
     */
    public Queue<Pair<String, String>> rebuildExpression_with_expression(String anAssert, List<OrderNode> orderMapList) {
        Queue<Pair<String, String>> queue = new LinkedList<>();
        String[] s1 = anAssert.split("\\s+");
        for (int i = 0; i < s1.length; i++) {
            String s2 = s1[i];
            if ("<".equals(s2) || ">".equals(s2)) {
                String target;
                String next;
                String var1 = s1[i + 1];
                String var2 = s1[i + 2];
                if ("<".equals(s2)) {
                    target = var1;
                    next = var2;
                } else {
                    target = var2;
                    next = var1;
                }

                boolean isConflict = isConflictWithOrderMap(target, next, orderMapList);
                String expression = "(" + s2 + " " + var1 + " " + var2 + " )";
                if (isConflict) {
                    queue.offer(new Pair<>("false", ""));
                } else {
                    queue.offer(new Pair<>("true", expression));
                }
                i += 2;
            } else if (!"(".equals(s1[i]) && !")".equals(s1[i]) && !"assert".equals(s1[i]) && !"".equals(s1[i].trim())) {
                queue.offer(new Pair<>(s1[i], s1[i]));
            }
        }
        return queue;
    }

    /**
     * Check if adding target > next conflicts with existing order map.
     * Returns true if there's already a path from next to target.
     */
    public boolean isConflictWithOrderMap(String target, String next, List<OrderNode> orderMapList) {
        OrderNode targetNode = null;
        OrderNode nextNode = null;
        for (OrderNode orderNode : orderMapList) {
            if (target.equals(orderNode.value)) {
                nextNode = orderNode;
            } else if (next.equals(orderNode.value)) {
                targetNode = orderNode;
            }
        }
        if (targetNode == null || nextNode == null) {
            return false;
        }
        return canFindRoute(targetNode, nextNode.value, orderMapList);
    }

    /**
     * Check if there's a path from currentNode to targetValue.
     */
    public boolean canFindRoute(OrderNode currentNode, String targetValue, List<OrderNode> orderMapList) {
        if (currentNode.largerNodes == null || currentNode.largerNodes.size() == 0) {
            return currentNode.value.equals(targetValue);
        }

        for (OrderNode largerNode : currentNode.largerNodes) {
            if (canFindRoute(largerNode, targetValue, orderMapList)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Create order map (directed graph) from variable constraints.
     */
    public List<OrderNode> createOrderMap(List<String[]> variables) {
        List<OrderNode> orderMap = new ArrayList<>();
        for (String[] variable : variables) {
            String option = variable[2];
            String target;
            String next;
            if ("<".equals(option)) {
                target = variable[0];
                next = variable[1];
            } else {
                target = variable[1];
                next = variable[0];
            }

            OrderNode targetNode = null;
            OrderNode nextNode = null;
            for (OrderNode orderNode : orderMap) {
                if (target.equals(orderNode.value)) {
                    targetNode = orderNode;
                } else if (next.equals(orderNode.value)) {
                    nextNode = orderNode;
                }
            }

            if (targetNode == null) {
                targetNode = new OrderNode(target);
                targetNode.largerNodes = new ArrayList<>();
                orderMap.add(targetNode);
            }
            if (nextNode == null) {
                nextNode = new OrderNode(next);
                nextNode.largerNodes = new ArrayList<>();
                orderMap.add(nextNode);
            }
            targetNode.largerNodes.add(nextNode);
        }
        return orderMap;
    }

    /**
     * Node class for representing directed acyclic graph.
     */
    static class OrderNode {
        public String value;
        public List<OrderNode> largerNodes;

        public OrderNode(String value) {
            this.value = value;
        }
    }
}
