package com.bc.tools;

import cn.hutool.crypto.digest.BCrypt;

import java.io.Console;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Scanner;

/**
 * 独立的密码重置工具（命令行小程序）。
 *
 * 用途：管理员忘记密码时，运行本工具，把数据库里指定账号的密码重置为新密码。
 * 密码依然用 BCrypt 单向哈希存储，不保存明文，因此安全可控。
 *
 * 用法：
 *   1. 在 tools/password-reset 目录执行 mvn package
 *   2. java -jar target/bcblog-password-reset.jar
 *   3. 按提示输入数据库信息和要重置的账号、新密码
 */
public class PasswordResetTool {

    public static void main(String[] args) {
        Console console = System.console();
        if (console != null) {
            run(console, null);
        } else {
            System.out.println("提示：当前环境无法隐藏密码输入，密码会明文显示。");
            run(null, new Scanner(System.in));
        }
    }

    private static void run(Console console, Scanner sc) {
        String host = readLine(console, sc, "数据库地址", "localhost");
        String port = readLine(console, sc, "数据库端口", "3306");
        String dbName = readLine(console, sc, "数据库名", "bc_blog");
        String dbUser = readLine(console, sc, "数据库账号", "root");
        String dbPassword = readPassword(console, sc, "数据库密码");
        String username = readLine(console, sc, "要重置密码的管理员用户名", "admin");
        String newPassword = readPassword(console, sc, "新密码（至少 8 位）");

        if (newPassword == null || newPassword.length() < 8) {
            System.out.println("密码长度至少 8 位，操作已取消。");
            return;
        }

        String url = "jdbc:mysql://" + host + ":" + port + "/" + dbName
                + "?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true";

        try (Connection conn = DriverManager.getConnection(url, dbUser, dbPassword);
             PreparedStatement ps = conn.prepareStatement("UPDATE sys_user SET password = ? WHERE username = ?")) {
            ps.setString(1, BCrypt.hashpw(newPassword));
            ps.setString(2, username);
            int rows = ps.executeUpdate();
            if (rows == 1) {
                System.out.println("重置成功：用户「" + username + "」的密码已更新。");
            } else {
                System.out.println("未找到用户「" + username + "」，请检查用户名是否正确。");
            }
        } catch (Exception e) {
            System.out.println("操作失败：" + e.getMessage());
        }
    }

    /** 读取普通文本，支持默认值 */
    private static String readLine(Console console, Scanner sc, String label, String def) {
        System.out.print(label + "（默认 " + def + "）：");
        String s;
        if (console != null) {
            s = console.readLine();
        } else {
            s = sc.nextLine();
        }
        if (s == null || s.trim().isEmpty()) {
            return def;
        }
        return s.trim();
    }

    /** 读取密码，优先使用 Console 隐藏输入 */
    private static String readPassword(Console console, Scanner sc, String label) {
        if (console != null) {
            char[] pwd = console.readPassword(label + "：");
            return pwd == null ? "" : new String(pwd);
        }
        System.out.print(label + "：");
        return sc.nextLine().trim();
    }
}
