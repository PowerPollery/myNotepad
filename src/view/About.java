package view;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

/**
 * 关于界面
 */

public class About extends JDialog{

    public About(EditWin editWin){
        setModal(true);
        //setUndecorated(true);//隐藏标题栏 - 只用于JFrame
        setTitle("关于记事本");
        setSize(500, 500);
        setLocationRelativeTo(null);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setLocationRelativeTo(editWin);
        setLayout(null);

        ImageIcon icon = new ImageIcon(About.class.getResource("/icons/notepad.png"));
        icon.setImage(icon.getImage().getScaledInstance(100,100,Image.SCALE_DEFAULT));
        JLabel label = new JLabel(icon);
        label.setBounds(190, 20, 100, 100);
        JSeparator separator = new JSeparator();
        separator.setBounds(10, 120, 460, 5);
        JTextArea ta = new JTextArea(10, 300);
        ta.setBounds(70, 150, 360, 240);
        ta.setBackground(new Color(240, 240, 240));
        ta.setFont(new Font("微软雅黑", 0, 15));
        ta.setEnabled(false);
        ta.setLineWrap(true);
        ta.setText("版本：记事本V2.3 (UTF8)\n\n作者：郑云瑞\n开发语言：Java\n开发环境：IntelliJ IDEA\n更新时间：2019-6-8\n\n简易记事本，记录每一天！\n- 加入了完美的高亮功能，记笔记写代码不再疲劳！\n- 打开方式优化。\n- 加入了代码模式，优化高亮优先级。\n- 修复若干BUG。");
        JButton button = new JButton("确定");
        button.setBounds(380, 400, 80, 30);
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                dispose();
            }
        });

        add(label);
        add(separator);
        add(ta);
        add(button);
        setVisible(true);
    }
}
