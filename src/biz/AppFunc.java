package biz;

import util.DTUtil;
import view.*;

import javax.swing.*;
import javax.swing.event.*;
import javax.swing.text.Document;
import javax.swing.undo.UndoManager;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.awt.event.*;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
/*本项目以UTF-8编码*/
/**
 * 应用功能
 * >2.0
 * >增加了拖拽打开文件功能
 * >自动换行菜单项不应该用ChangeListener而应该用Action
 * >使用了忽略\r的判等
 * >加入了高亮功能
 * >加入了回撤功能
 * >撤销/回撤功能忽略了样式改变，只记录文本变动
 * >修复了打开另一个文件仍然能撤销上个文件内容的bug
 *
 * >在文本变化时加入了是否高亮判断，避免大量无用线程
 * >2.1 - 可以直接在文件的打开方式中设置以notepad打开
 *      - 改变了选中颜色
 *      - 加入了查找替换功能
 * >2.2 - 增加了unimportant高亮的优先级，标置为?，是最不重要的队列，提高了高亮的丰富性
 *      - 将TAB键规定为4个空格
 *      - 增加了自动缩进功能：回车后与上一个非空行对齐，如果在大括号中间的话...你懂的
 *      - 增加了()[]{}''""符号辅助添加的功能
 *      - 增加了自动退格功能，跟idea类似
 *      - 高亮中添加了canDivided属性(配置文件中以单独~号标识)，即是否可被分割，在todo和action中均加入了中断高亮的判断
 *        不过在action中，不可分割属性目前只适用于PART类型的高亮，如''号。解决了在注释中的'号造成高亮混乱的问题。
 *      - 增加了背景颜色的高亮
 *      - 丰富状态栏提示信息
 *      - 查找替换 界面靠右上 - 每次打开都是
 * >2.3 - 去掉了文件打开器的文件过滤
 *      - 记录上次打开文件路径(3.1新增)
 *      - 加入了快捷注释//功能(3.1新增)
 *      - 查找支持了正则(3.0新增)
 *      # 在Opener中加入了用不同字符集读取文件的功能
 *      # 选择高亮延迟问题优化
 */

/**
 * BUG
 * 2.0  在打开一个文件后，用输入法打字，进行撤销时会混乱甚至卡住。...
 * 2.1  不高亮问题 - 少了个!号 - 已解决
 * 2.2  修复了首次打开文件会每行多一个回车的bug，具体在Opener
 * 2.2  查找时焦点可以移到替换框内，并用快捷键进行替换 - 已解决
 * 2.2  在代码模式下，选中一段内容后，触发自动生成的键，生成的内容不会替换所选内容，甚至出BUG。- 已解决
 * 2.2  背景高亮只会在有前景样式时显示 - 已解决
 * 2.3  高亮文件夹缺失时打不开 - 已解决
 * 2.3  修复点“无”时勾会消失的bug
 */
public class AppFunc {
    public EditWin editWin;
    private Document document;
    private UndoManager undo;
    private boolean hasReset = false;
    //高亮线程
    private Thread t_highlight;
    //高亮设置文件
    private String highlightSettingName = DTUtil.getHighlightName();
    //暂停高亮相应
    private boolean pauseHlt = false;

    //右键菜单
    private JPopupMenu popup;
    private MyMenuItem iCopy, iPaste, iCut, iDelete, iSelectAll, iFomart;
    /*菜单事件*/
    public static final int OPEN = 1;
    public static final int SAVE = 2;
    public static final int SAVE_ANOTHER = 3;
    public static final int FONT = 4;
    public static final int NEW = 5;
    public static final int ABOUT = 6;
    public static final int COUNT = 7;
    public static final int NOTES = 8;
    public static final int FIND = 9;
    public static final int REPLACE = 10;

    public AppFunc(EditWin editWin){
        this.editWin = editWin;

        undo = new UndoManager();
        iCopy = new MyMenuItem("复制(C)");
        iPaste = new MyMenuItem("粘贴(V)");
        iCut = new MyMenuItem("剪切(X)");
        iDelete = new MyMenuItem("删除(D)");
        iSelectAll = new MyMenuItem("全部选中(A)");
        iFomart = new MyMenuItem("CSS格式化");
        popup = new JPopupMenu();
        popup.add(iCut);
        popup.add(iCopy);
        popup.add(iPaste);
        popup.add(iDelete);
        popup.addSeparator();
        popup.add(iSelectAll);
        popup.add(iFomart);
        //这里必须要是area去add
        editWin.getTextPane().add(popup);

        addHandler();
        addListener();

    }

    //处理菜单事件
    public void menuDeal(int event){
        /* 才发现多线程会让同一个方法效果不一样！
         * 在另一个线程中执行open，如果open中只有光标置前的方法话，主线程的滚动条并不会跟着光标走，所以需要设置滚动条
         */
        new Thread(){
            @Override
            public void run() {
                if(event == OPEN)
                    open(null);
                else if(event == SAVE)
                    save();
                else if(event == SAVE_ANOTHER)
                    saveAnother();
                else if(event == FONT)
                    chooseFont();
                else if(event == NEW)
                    newOne();
                else if(event == ABOUT)
                    about();
                else if(event == COUNT)
                    count();
                else if(event == NOTES)
                    notes();
                else if(event == FIND)
                    find();
                else if(event == REPLACE)
                    replace();

            }
        }.start();
    }

    //代码模式
    private void onCodeModel(){
        if(!editWin.getTextPane().getCodeMode()){
            editWin.getTextPane().setCodeMode(true);
            DTUtil.setCodeMode(true);
            editWin.getiCode().setState(true);
            editWin.showStatus("代码模式");
        }else{
            editWin.getTextPane().setCodeMode(false);
            DTUtil.setCodeMode(false);
            editWin.getiCode().setState(false);
            editWin.showStatus("退出代码模式");
        }
    }

    //查找
    private void find(){
        FindAndReplace.getInstance("find", editWin);
    }
    //替换
    private void replace(){
        FindAndReplace.getInstance("replace", editWin);
    }

    //准备高亮
    public void prepareHighlight(){
        if(editWin.getFilePath() == null)
            return;
        File nowFile = new File(editWin.getFilePath());
        if(nowFile == null)
            return;
        String nowFileName = nowFile.getName();
        editWin.prepareHighlight(highlightSettingName, nowFileName.substring(nowFileName.lastIndexOf('.'), nowFileName.length()));
    }
    //高亮
    public void highlight(){
        if(!pauseHlt)
            editWin.highlight();
    }
    //先消除样式的高亮
    public void highlight(int offset, int length){
        if(!pauseHlt)
            editWin.highlight(offset, length);
    }
    //高亮线程工作
    public void onHighlight(int offset, int length){
        //滤掉冗余的情况
        if(editWin.getTextPane().getSHighlighter() == null  ||
                !editWin.getTextPane().getSHighlighter().hasPrepared()){
            return;
        }
        if(t_highlight != null && t_highlight.isAlive())
            t_highlight.stop();
        t_highlight = new Thread(){
            @Override
            public void run() {
                highlight(offset, length);
            }
        };
        t_highlight.start();
    }
    //复制
    public void copy(){
        if(editWin.getTextPane().getSelectedText() == null){//没有选中文字
            editWin.showStatus("请先选中内容！");
            return;
        }
        // 获取系统剪贴板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        // 封装文本内容
        Transferable trans = new StringSelection(editWin.getTextPane().getSelectedText());
        // 把文本内容设置到系统剪贴板
        clipboard.setContents(trans, null);
        editWin.showStatus("已复制");
    }
    //粘贴
    public void paste(){
        // 获取系统剪贴板
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        // 获取剪贴板中的内容
        Transferable trans = clipboard.getContents(null);
        if (trans != null) {
            // 判断剪贴板中的内容是否支持文本
            if (trans.isDataFlavorSupported(DataFlavor.stringFlavor)) {
                try {
                    // 获取剪贴板中的文本内容
                    String text = (String) trans.getTransferData(DataFlavor.stringFlavor);
                    //这里要先删除选中内容
                    cut();
                    //插入
                    editWin.getTextPane().insert(text, editWin.getTextPane().getCaretPosition());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
    //剪切
    public void cut(){
        if(editWin.getTextPane().getSelectedText() == null){//没有选中文字
            editWin.showStatus("请先选中内容！");
            return;
        }
        copy();
        editWin.getTextPane().replaceRange("", editWin.getTextPane().getSelectionStart(), editWin.getTextPane().getSelectionEnd());
        editWin.showStatus("已剪切");
    }
    //删除
    public void delete(){
        if(editWin.getTextPane().getSelectedText() == null){//没有选中文字
            editWin.showStatus("请先选中内容！");
            return;
        }
        editWin.getTextPane().replaceRange("", editWin.getTextPane().getSelectionStart(), editWin.getTextPane().getSelectionEnd());
        editWin.showStatus("已删除");
    }

    //笔记
    public void notes(){
        new Notes(this);
    }
    //字数统计
    public void count(){
        new Counter(editWin);
    }
    //关于
    public void about(){
        new About(editWin);
    }
    //新建
    public void newOne(){
        if(contentChange()){
            int option = JOptionPane.showConfirmDialog(editWin, "是否保存文档？");
            if(option == JOptionPane.OK_OPTION){
                new Saver(editWin);
            }else if(option == JOptionPane.NO_OPTION){
            }else{
                return;
            }
        }
        //新建界面
        editWin.reBegin();

        //撤销器重置
        undo.discardAllEdits();
    }
    //打开
    public void open(File file){
        if(contentChange()){
            int option = JOptionPane.showConfirmDialog(editWin, "是否保存文档？");
            if(option == JOptionPane.OK_OPTION){
                new Saver(editWin);
            }else if(option == JOptionPane.NO_OPTION){
            }else{
                return;
            }
        }
        //传进了文件
        if(file != null){
            new Opener(editWin, file, DTUtil.getCharset());
            afterOpen();
            return;
        }
        //自选文件
        new Opener(editWin, DTUtil.getCharset());
        afterOpen();

    }
    //打开之后的操作
    public void afterOpen(){
        //准备高亮
        prepareHighlight();
        //高亮
        highlight();
        //撤销器重置
        undo.discardAllEdits();
    }
    //保存
    public void save(){
        new Saver(editWin);
        prepareHighlight();
        highlight();
    }
    //另存为
    public void saveAnother(){
        new Saver(editWin, null);
        prepareHighlight();
    }
    //选择字体
    public void chooseFont(){
        new FontChooser().showChooser(editWin);
    }
    public void closing(){
        if(contentChange()){
            //有改动
            int option = JOptionPane.showConfirmDialog(editWin, "是否保存文档？");
            if(option == JOptionPane.OK_OPTION){
                new Saver(editWin);
            }else if(option == JOptionPane.NO_OPTION){
            }else{
                return;
            }
        }
        exit();
    }
    //退出
    public void exit(){
        //如果没有点重置，那么就保存以下设置
        if(!hasReset) {
            //保存是否最大化
            if(editWin.getExtendedState()==JFrame.MAXIMIZED_BOTH){//最大化了
                DTUtil.setMaxFrame(true);
            }else{
                DTUtil.setMaxFrame(false);

                DTUtil.setX(editWin.getX());
                DTUtil.setY(editWin.getY());
                DTUtil.setWidth(editWin.getWidth());
                DTUtil.setHeight(editWin.getHeight());
            }
        }
        editWin.closeAnimation();
        System.exit(0);
    }


    //重置
    public void reset(){
        editWin.getTextPane().setFont(new Font(FontChooser.fontsName[DTUtil.getFontIndex()], DTUtil.getStyleIndex(), DTUtil.getFontSize()+10));//加数字
        editWin.getiLineWrap().setState(DTUtil.getLineWrap());
    }
    //文本变动
    public void textChange(){
        editWin.showStatus("就绪");
        //是否改动
        if(editWin.getContent() != null){//content等于null代表目前没有打开任何已存在文件
            if(contentChange()){
                editWin.setTitle("*"+editWin.getFilePath()+" - 记事本");
            }else{
                editWin.setTitle(editWin.getFilePath()+" - 记事本");
            }
        }

    }
    //内容是否变动
    public boolean contentChange(){
        //这里如果按以前的代码的话，一样的内容getText()和content里的竟然不一样，好像是因为textPane里的回车是\r\n
        if(editWin.getContent()==null && !editWin.getTextPane().getText().equals("") ||
                editWin.getContent()!=null && !deREquals(editWin.getContent(), editWin.getTextPane().getText())){//这里忽略了\r
            return true;
        }
        return false;
    }
    //忽略\r的判等
    public boolean deREquals(String str1, String str2){
        //先把\r都去掉
        str1 = str1.replaceAll("\r", "");
        str2 = str2.replaceAll("\r", "");
        int len1 = str1.length();
        int len2 = str2.length();
        if(len1 != len2)
            return false;
        for(int i = 0; i < len1; i++){
            if(str1.charAt(i) != str2.charAt(i))
                return false;
        }
        return true;
    }

    public void addHandler(){
        //为文本框添加数据传输器（拖拽功能）
        //实现后，swing原有的支持剪切、复制和粘贴的键盘绑定的功能会失效，只需自己监听即可
        editWin.getTextPane().setTransferHandler(new TransferHandler(){
            @Override
            public boolean importData(JComponent comp, Transferable t) {
                try {
                    //这里加判断是因为实现拖拽功能后再进行粘贴等键盘操作时会出异常，而异常就是UnsupportedDataFlavor
                    if(!t.isDataFlavorSupported(DataFlavor.javaFileListFlavor))
                        return false;
                    Object o = t.getTransferData(DataFlavor.javaFileListFlavor);
                    List list = (List) o;//文件列表
                    open(new File(list.get(0).toString()));//只取第一个文件
                    return true;
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
                return false;
            }
            @Override
            public boolean canImport(JComponent comp, DataFlavor[] flavors) {
                for (int i = 0; i < flavors.length; i++) {
                    if (DataFlavor.javaFileListFlavor.equals(flavors[i])) {
                        return true;
                    }
                }
                return false;
            }
        });
    }

    //文本监听，之所以单独列出来是因为换行策略更改后document会随之变化，之前的监听器将失效，需要再次注册
    //这里每次开始新的高亮线程之前都停止之前的线程，保证了同一时间内只有一个高亮线程
    public void docListen(){
        document = editWin.getTextPane().getDocument();
        document.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                textChange();
                onHighlight(e.getOffset(), e.getLength());
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                textChange();
                onHighlight(e.getOffset(), e.getLength());
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                //这个bug让我找了好长时间，尽量不用这个方法，否则有可能无限循环
            }
        });
    }


    public void addListener(){
        //文本监听
        docListen();
        //菜单监听
        editWin.getiOpen().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(OPEN);
            }
        });
        editWin.getiSave().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(SAVE);
            }
        });
        editWin.getiSaveAnother().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(SAVE_ANOTHER);
            }
        });
        editWin.getiFont().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(FONT);
            }
        });
        editWin.getiLineWrap().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                if(editWin.getiLineWrap().getState() == true){//被勾选了
                    editWin.getTextPane().setLineWrap(true);
                    DTUtil.setLineWrap(true);
                }else {
                    editWin.getTextPane().setLineWrap(false);
                    DTUtil.setLineWrap(false);
                }
                //再次注册监听器
                docListen();
                //准备高亮
                prepareHighlight();
            }
        });
        editWin.getiReset().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                DTUtil.initFile();
                reset();
                hasReset = true;
                editWin.showStatus("已恢复所有默认设置");
            }
        });
        editWin.getiNew().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(NEW);
            }
        });
        editWin.getiAbout().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(ABOUT);
            }
        });
        editWin.getiCount().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(COUNT);
            }
        });
        editWin.getiDate().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editWin.getTextPane().append(new SimpleDateFormat("yyyy/MM/dd").format(new Date()));
            }
        });
        editWin.getiNote().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(NOTES);
            }
        });
        editWin.getiFind().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(FIND);
            }
        });
        editWin.getiReplace().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                menuDeal(REPLACE);
            }
        });
        editWin.getiCode().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                //代码模式
                onCodeModel();
            }
        });
        iCopy.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                copy();
            }
        });
        iPaste.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                paste();
            }
        });
        iCut.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                cut();
            }
        });
        iDelete.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                delete();
            }
        });
        iSelectAll.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editWin.getTextPane().selectAll();
            }
        });
        iFomart.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                editWin.format();
            }
        });


        //窗口监听
        editWin.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                //关闭
                closing();
            }
        });
        //键盘监听
        editWin.getTextPane().addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_S) {//Ctrl组合键的写法
                    save();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_O) {
                    open(null);
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_P) {
                    saveAnother();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_T) {
                    chooseFont();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_N) {
                    newOne();
                }else if(e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                    closing();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_X) {
                    cut();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_C) {
                    copy();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_V) {
                    paste();
                }else if(e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_ENTER) {//快速换行
                    editWin.quickWrap();
                }else if(e.isControlDown() && !e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_Z){
                    //撤销
                    if(undo.canUndo()) {
                        undo.undo();
                    }
                }else if(e.isControlDown() && e.isShiftDown() && e.getKeyCode() == KeyEvent.VK_Z){
                    //回撤
                    if(undo.canRedo()) {
                        undo.redo();
                    }
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_D){
                    delete();
                }else if(e.getKeyCode() == KeyEvent.VK_F1){
                    count();
                }else if(e.getKeyCode() == KeyEvent.VK_F2){
                    editWin.getTextPane().append(new SimpleDateFormat("yyyy/MM/dd").format(new Date()));
                }else if(e.getKeyCode() == KeyEvent.VK_F3){
                    notes();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F12){
                    //测试键
                    editWin.getTextPane().text();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_F){
                    find();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_R) {
                    replace();
                }else if(e.isControlDown() && e.getKeyCode() == KeyEvent.VK_BACK_SLASH) {
                    onCodeModel();
                }
            }
        });
        //鼠标监听
        editWin.getTextPane().addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                //右键
                if(e.getButton() == MouseEvent.BUTTON3){
                    popup.show(editWin.getTextPane(), e.getX(), e.getY());
                }
            }
        });
        //焦点监听
        editWin.getTextPane().addFocusListener(new FocusListener() {
            @Override
            public void focusGained(FocusEvent e) {
                highlight();
            }

            @Override
            public void focusLost(FocusEvent e) {

            }
        });
        //高亮菜单监听
        for(JCheckBoxMenuItem item : editWin.getHighlightItems()){
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    highlightSettingName = item.getLabel();
                    DTUtil.setHighlightName(highlightSettingName);
                    //其他的取消勾
                    for(JCheckBoxMenuItem other : editWin.getHighlightItems()){
                        other.setState(false);
                    }
                    item.setState(true);
                    //“无”取消
                    editWin.getiNoHL().setState(false);
                    new Thread(){
                        @Override
                        public void run() {
                            prepareHighlight();
                            highlight();
                        }
                    }.start();
                }
            });
        }
        editWin.getiNoHL().addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                highlightSettingName = null;
                DTUtil.setHighlightName(null);
                //其他的取消勾
                for(JCheckBoxMenuItem other : editWin.getHighlightItems()){
                    other.setState(false);
                }
                editWin.getiNoHL().setState(true);//自己不取消
                prepareHighlight();
                editWin.getTextPane().defaultView();
            }
        });
        //编码项监听
        for(JCheckBoxMenuItem item : editWin.getCharsetItems()){
            item.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    DTUtil.setCharset(item.getLabel());

                    if(editWin.getFilePath() != null) {
                        new Thread(){
                            @Override
                            public void run() {
                                pauseHlt = true;//高亮先暂停
                                open(new File(editWin.getFilePath()));
                                pauseHlt = false;
                                highlight();
                            }
                        }.start();
                    }
                    for(JCheckBoxMenuItem other : editWin.getCharsetItems()){
                        other.setState(false);
                    }
                    item.setState(true);
                }
            });
        }
        //撤销监听
        editWin.getTextPane().getDocument().addUndoableEditListener(new UndoableEditListener() {
            @Override
            public void undoableEditHappened(UndoableEditEvent e) {
                //只撤销添加和删除操作
                if(e.getEdit().getPresentationName().equals("添加") || e.getEdit().getPresentationName().equals("删除"))
                    undo.addEdit(e.getEdit());
            }
        });
    }
}
