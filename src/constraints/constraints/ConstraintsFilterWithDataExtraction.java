package constraints.constraints;

import javafx.util.Pair;
import constraints.config.Configuration;
import java.util.*;
import java.io.*;

/**
 * Enhanced constraints filter that both filters constraints and extracts data
 * for analysis and research purposes. This version includes data extraction,
 * file output, and detailed statistics.
 */
public class ConstraintsFilterWithDataExtraction {

    // Statistics and configuration - 简化为GC-MCR_的逻辑
    public static long totalConstraintsCounts = 0;
    public static long filtedConstraintsCounts = 0;
    public static int filterCount = 0;
    public static boolean fconexist = false;
    public static boolean fcauexist = false;
    public static boolean ftagexist = false;
    public static String basePath = "./data/Conhash/"; // 指定文件congraph,casual和tag的文件夹

    // Debug counters for useful data
    public static int con = 0;
    public static int cau = 0;

    /**
     * Filter constraints with data extraction capabilities.
     *
     * @param CONS_ASSERT_PO    Program order constraints
     * @param CONS_ASSERT_VALID Lock-related constraints
     * @param causalConstraint  New read-write constraints
     * @return Pair<Boolean, StringBuilder> where Boolean indicates if constraints are satisfiable,
     *         and StringBuilder contains the filtered constraint if available
     */
    public Pair<Boolean, StringBuilder> doFilter_with_expression(StringBuilder CONS_ASSERT_PO, StringBuilder CONS_ASSERT_VALID, StringBuilder causalConstraint) {
        try {
            cau = 0;
            con = 0;
            StringBuilder result = new StringBuilder("");
            List<String[]> variables = new ArrayList<>();
            variables.addAll(findVariable(CONS_ASSERT_PO.toString()));
            variables.addAll(findVariable(CONS_ASSERT_VALID.toString()));

            // Build adjacency list from program order and lock constraints
            List<OrderNode> orderMapList = createOrderMap(variables);
            con = orderMapList.size();

            // Process causal constraints
            StringBuilder sb = new StringBuilder();
            String causalStr = causalConstraint.toString();

            // 处理PCR的命名约束格式 (! ... :named INTER_X) -> 提取真正的约束内容
            // 同时处理多层嵌套的情况
            causalStr = causalStr.replaceAll("\\(assert\\s*\\(\\s*!\\s+([^:]+?)\\s*:named[^)]+\\)\\s*\\)", "(assert $1)");

            // 如果还有残留的命名约束格式，继续处理
            causalStr = causalStr.replaceAll("\\(\\s*!\\s+([^:]+?)\\s*:named[^)]+\\)", "$1");

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

            // Extract data for analysis

            data_output(variables, assertsString, basePath);

            // Check each assertion for conflicts
            for (String anAssert : assertsString) {
                System.out.println(orderMapList);
                Queue<Pair<String, String>> rebuildCausalConstraint = rebuildExpression_with_expression(anAssert, orderMapList);
                System.out.println("1111");
                System.out.println(rebuildCausalConstraint.toString());
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
                        tagupload(basePath + "tag.txt", "0");
                        return new Pair<>(false, result);
                    } else {
                        String constraint = stack.get(0).getValue();
                        // 只有当约束内容不为空且不是纯"true"时才添加（参照GC-MCR_逻辑）
                        if (constraint != null && !constraint.trim().isEmpty() &&
                                !"true".equals(constraint.trim())) {
                            result.append("(assert " + constraint + ")\n");
                        }
                    }
                }
            }
            return new Pair<>(true, result);
        } catch (Exception e) {
            System.err.println("Error in constraint filtering: " + e.getMessage());
            return new Pair<>(true, causalConstraint);
        }
    }

    /**
     * Extract variable relationships from constraint string.
     */
    public List<String[]> findVariable(String s) {
        List<String[]> result = new ArrayList<>();
        if (s.contains("or")) {
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
        try {
            if (currentNode.largerNodes == null || currentNode.largerNodes.size() == 0) {
                return currentNode.value.equals(targetValue);
            }

            for (OrderNode largerNode : currentNode.largerNodes) {
                if (canFindRoute(largerNode, targetValue, orderMapList)) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            System.err.println("Error in route finding: " + orderMapList);
            return false;
        }
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
     * Extract and output constraint data for analysis.
     * 完全按照GC-MCR_的简单逻辑实现
     */
    public void data_output(List<String[]> list, List<String> assertsString, String base) {
        String conGraphpath = base + "conGraph.txt";
        String casualpath = base + "casual.txt";

        StringBuilder conGraph = new StringBuilder();
        StringBuilder casual = new StringBuilder();

        // Process constraint graph
        for (String[] edge : list) {
            StringBuilder var0 = new StringBuilder(edge[0]);
            StringBuilder var1 = new StringBuilder(edge[1]);

            var0.deleteCharAt(0);
            var1.deleteCharAt(0);

            conGraph.append(var0.toString()).append(" ");
            conGraph.append(var1.toString()).append(" ");
            if (edge[2].equals("<")) {
                conGraph.append("-1 ");
            } else {
                conGraph.append("1 ");
            }
        }

        String tmp = convert_Constraints(assertsString);

        // Only output if we have useful data
        if (con == 0 || cau == 0) {
            return;
        }

        // GC-MCR_风格：只在第一次调用时初始化文件
        if (filterCount == 0) {
            File file1 = new File(conGraphpath);
            File file2 = new File(casualpath);
            if (file1.exists()) {
                fconexist = true;
            } else {
                try {
                    file1.getParentFile().mkdirs();
                    file1.createNewFile();
                    System.out.println("Created file: " + conGraphpath);
                } catch (IOException e) {
                    System.err.println("Failed to create file: " + e.getMessage());
                }
            }
            if (file2.exists()) {
                fcauexist = true;
            } else {
                try {
                    file2.getParentFile().mkdirs();
                    file2.createNewFile();
                    System.out.println("Created file: " + casualpath);
                } catch (IOException e) {
                    System.err.println("Failed to create file: " + e.getMessage());
                }
            }
        }

        // 写入约束图数据（GC-MCR_风格：只有当文件不存在时）
        if (!fconexist) {
            synchronized(this) {
                try {
                    FileWriter fw1 = new FileWriter(conGraphpath, true);
                    BufferedWriter bw1 = new BufferedWriter(fw1);
                    bw1.write(conGraph.toString());
                    bw1.write("\n");
                    bw1.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        // 写入因果约束数据（GC-MCR_风格：只有当文件不存在时）
        if (!fcauexist) {
            synchronized(this) {
                try {
                    FileWriter fw2 = new FileWriter(casualpath, true);
                    BufferedWriter bw2 = new BufferedWriter(fw2);
                    bw2.write(tmp);
                    bw2.write("\n");
                    bw2.flush();
                    bw2.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Convert constraints to string format for data extraction.
     * 使用前序遍历树的标准方法，类似GC-MCR_
     */
    public String convert_Constraints(List<String> assertsString) {
        if (assertsString.isEmpty()) {
            return "";
        }

        // 处理单个或多个约束
        String str;
        if (assertsString.size() == 1) {
            str = assertsString.get(0);
        } else {
            // 合并多个约束 - 参考GC-MCR_的combineString逻辑
            str = combineConstraints(assertsString.get(0), assertsString.get(1));
        }

        // 分割字符串为tokens
        List<String> tokens = splitStringBySpace(str);
        if (tokens.isEmpty()) {
            return "";
        }

        // 构建表达式树
        Stack<VarNode> stack = new Stack<>();
        VarNode root = new VarNode(tokens.get(0));
        stack.push(root);

        for (int i = 1; i < tokens.size(); i++) {
            String token = tokens.get(i);
            VarNode node = new VarNode(token);

            if (isOperator(token)) {
                // 操作符节点
                while (true) {
                    if (stack.peek().left == null) {
                        stack.peek().left = node;
                        node.fatherNode = stack.peek();
                        break;
                    } else if (stack.peek().right == null) {
                        stack.peek().right = node;
                        node.fatherNode = stack.peek();
                        break;
                    } else {
                        stack.pop();
                    }
                }
                stack.push(node);
            } else {
                // 操作数节点
                while (true) {
                    if (stack.peek().left == null) {
                        stack.peek().left = node;
                        node.fatherNode = stack.peek();
                        break;
                    } else if (stack.peek().right == null) {
                        stack.peek().right = node;
                        node.fatherNode = stack.peek();
                        break;
                    } else {
                        stack.pop();
                    }
                }
            }
        }

        // 确保只有一个根节点
        while (stack.size() > 1) {
            stack.pop();
        }

        VarNode finalRoot = stack.peek();

        // 优化树结构
        tree_optimize(finalRoot);

        // 检查是否是简单值
        if (isSimple(finalRoot)) {
            return "";
        }

        // 前序遍历输出
        String result = preorderTraversal(finalRoot);
        if (!result.isEmpty()) {
            cau = 1;
        }

        return result;
    }

    /**
     * 合并两个约束字符串
     */
    private String combineConstraints(String str1, String str2) {
        return "and " + str1 + " " + str2;
    }

    /**
     * 按空格分割字符串
     */
    private List<String> splitStringBySpace(String str) {
        List<String> result = new ArrayList<>();
        String[] tokens = str.trim().split("\\s+");
        for (String token : tokens) {
            if (!token.isEmpty()) {
                result.add(token);
            }
        }
        return result;
    }

    /**
     * 判断是否是操作符
     */
    private boolean isOperator(String token) {
        return "and".equals(token) || "or".equals(token) || "<".equals(token) || ">".equals(token);
    }

    /**
     * 树优化 - 参考GC-MCR_的tree_optimize
     */
    private void tree_optimize(VarNode root) {
        if (root == null) {
            return;
        }

        if ("and".equals(root.value)) {
            if (root.left != null && "true".equals(root.left.value)) {
                // and true X -> X
                root.value = root.right.value;
                root.left = root.right.left;
                root.right = root.right.right;
                if (root.right != null) root.right.fatherNode = root;
                if (root.left != null) root.left.fatherNode = root;
                tree_optimize(root);
            } else if (root.right != null && "true".equals(root.right.value)) {
                // and X true -> X
                root.value = root.left.value;
                root.right = root.left.right;
                root.left = root.left.left;
                if (root.right != null) root.right.fatherNode = root;
                if (root.left != null) root.left.fatherNode = root;
                tree_optimize(root);
            } else if (root.left != null && "false".equals(root.left.value)) {
                // and false X -> false
                root.value = "false";
                root.left = null;
                root.right = null;
            } else if (root.right != null && "false".equals(root.right.value)) {
                // and X false -> false
                root.value = "false";
                root.right = null;
                root.left = null;
            } else {
                tree_optimize(root.left);
                tree_optimize(root.right);
            }
        } else if ("or".equals(root.value)) {
            if (root.left != null && "false".equals(root.left.value)) {
                // or false X -> X
                root.value = root.right.value;
                root.left = root.right.left;
                root.right = root.right.right;
                if (root.right != null) root.right.fatherNode = root;
                if (root.left != null) root.left.fatherNode = root;
                tree_optimize(root);
            } else if (root.right != null && "false".equals(root.right.value)) {
                // or X false -> X
                root.value = root.left.value;
                root.right = root.left.right;
                root.left = root.left.left;
                if (root.right != null) root.right.fatherNode = root;
                if (root.left != null) root.left.fatherNode = root;
                tree_optimize(root);
            } else if (root.left != null && "true".equals(root.left.value)) {
                // or true X -> true
                root.value = "true";
                root.left = null;
                root.right = null;
            } else if (root.right != null && "true".equals(root.right.value)) {
                // or X true -> true
                root.value = "true";
                root.left = null;
                root.right = null;
            } else {
                tree_optimize(root.left);
                tree_optimize(root.right);
            }
        }
        else if("true".equals(root.value)){
            tree_optimize(root.fatherNode);
        }
        else if("false".equals(root.value)){
            tree_optimize(root.fatherNode);
        }
        else {
            tree_optimize(root.left);
            tree_optimize(root.right);
        }
    }

    /**
     * 检查是否是简单的true/false值
     */
    private boolean isSimple(VarNode root) {
        return "true".equals(root.value) || "false".equals(root.value);
    }

    /**
     * 前序遍历输出树结构
     */
    private String preorderTraversal(VarNode root) {
        if (root == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();
        result.append(root.value);

        if (root.left != null) {
            String leftStr = preorderTraversal(root.left);
            if (!leftStr.isEmpty()) {
                result.append(" ").append(leftStr);
            }
        }

        if (root.right != null) {
            String rightStr = preorderTraversal(root.right);
            if (!rightStr.isEmpty()) {
                result.append(" ").append(rightStr);
            }
        }

        return result.toString();
    }

    /**
     * 树节点类 - 参考GC-MCR_的VarNode
     */
    private static class VarNode {
        String value;
        VarNode left;
        VarNode right;
        VarNode fatherNode;

        VarNode(String value) {
            this.value = value;
        }
    }

    /**
     * Upload tag data for analysis.
     * 完全按照GC-MCR_的简单逻辑实现
     */
    public static void tagupload(String path, String tag) {
        // GC-MCR_风格：只在第一次调用时初始化文件
        System.out.println("222");
        System.out.println(filterCount);
        if (filterCount == 0) {
            System.out.println("here");
            File file = new File(path);
            if (file.exists()) {
                ftagexist = true;
            } else {
                try {
                    file.getParentFile().mkdirs();
                    file.createNewFile();
                    System.out.println("Created file: " + path);
                } catch (IOException e) {
                    System.err.println("Failed to create file: " + e.getMessage());
                }
            }
        }

        if (con == 0 || cau == 0) {
            return;
        }

        // GC-MCR_风格：只有当文件不存在时才写入
        if (!ftagexist) {
            synchronized(ConstraintsFilterWithDataExtraction.class) {
                try {
                    FileWriter fw = new FileWriter(path, true);
                    BufferedWriter bw = new BufferedWriter(fw);
                    bw.write(tag);
                    bw.write("\n");
                    bw.close();
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
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

        public String getLargerNodes() {
            StringBuilder res = new StringBuilder();
            if (largerNodes != null) {
                for (OrderNode largerNode : largerNodes) {
                    res.append(largerNode.value).append(",");
                }
            }
            return res.toString();
        }

        @Override
        public String toString() {
            return "OrderNode{" +
                    "value='" + value + '\'' +
                    ", largerNodes=" + getLargerNodes() +
                    '}';
        }
    }
}
