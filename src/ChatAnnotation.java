

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
/**
 * 消息分成三类
 * 1.建立会话连接的消息
 * 2.关闭会话连接的消息
 * 3.对话消息
 *
 *前两类是制定的内容，不需要进行过滤
 *第三类需要将和js代码冲突的字符转换一下，进行所谓的过滤
 *
 */

/**
 * tomcat在本程序中的作用
 * tomcat会持续监听
 * 一旦有事件发生就执行相应的方法
 * 
 * 比如有新的用户加入聊天
 * tomcat会执行下面的@OnOpen方法
 *
 */


@ServerEndpoint(value = "/websocket/{userid}")

public class ChatAnnotation {
    private static final String GUEST_PREFIX = "用户";  
    /** 
     * 一个提供原子操作的Integer的类。在Java语言中，++i和i++操作并不是线程安全的， 
     * 在使用的时候，不可避免的会用到synchronized关键字。 而AtomicInteger则通过一种线程安全的加减操作接口。 
     */  
    private static final AtomicInteger connectionIds = new AtomicInteger(0);  
    private static final Set<ChatAnnotation> connections = new CopyOnWriteArraySet<>();  
  
    private  String nickname;  
    private Session session;  
  
    public ChatAnnotation() {  
        nickname = GUEST_PREFIX + connectionIds.getAndIncrement();  
    }  
  
    /** 
     * 创建连接时间调用的方法 
     *  
     * @param session 
     */  
    @OnOpen  
    public void start(@PathParam("userid") String userid,Session session) { 
    	nickname=userid;
        this.session = session;  
        connections.add(this);  
        String message = String.format("* %s %s", nickname, "加入聊天室");  
        //上线通知  
        broadcast(message);  
        try {  
            //系统问候语  
            SendHello(this.nickname);  
            //返回在线用户  
            onlineList();
        } catch (IOException e) {  
            e.printStackTrace();  
        }  
          
    }  
  
    /** 
     * 链接关闭时调用方法 
     * @throws IOException 
     */  
    @OnClose  
    public void end(@PathParam("userid") String userid) throws IOException {  
    	nickname=userid;
        connections.remove(this);  
        String message = String.format("* %s %s", nickname, "退出聊天室");  
        broadcast(message);
        onlineList();
    }  
    /** 
     * 传输信息过程中调用方法 
     * @param message 
     */  
    @OnMessage  
    public void incoming(@PathParam("userid") String userid,String message) {  
        // Never trust the client  
        // TODO: 过滤输入的内容  
    	nickname=userid;
        String m = String.format("* %s %s", nickname, message);  
        if(m.contains("to")){  
            //点对点发送  
            broadcastOneToOne(m,nickname);  
        }else{  
            //群发  
            broadcast(m);  
        }  
    }  
    /** 
     * 发生错误是调用方法 
     * @param t 
     * @throws Throwable 
     */  
    @OnError  
    public void onError(Throwable t) throws Throwable {  
        System.out.println("错误: " + t.toString());  
    }  
    /** 
     * 消息广播 
     * 通过connections，对所有其他用户推送信息的方法 
     * @param msg 
     */  
    private static void broadcast(String msg) {  
        for (ChatAnnotation client : connections) {  
            try {  
                synchronized (client) {  
                    client.session.getBasicRemote().sendText(msg);  
                }  
            } catch (IOException e) {  
                System.out.println("错误:向客户端发送消息失败");  
                connections.remove(client);  
                try {  
                    client.session.close();  
                } catch (IOException e1) {  
                    e1.printStackTrace();  
                }  
                String message = String.format("* %s %s", client.nickname,"退出聊天室");  
                broadcast(message);  
            }  
        }  
    }  
    /** 
     * 一对一发送消息
     * 类似于broadcast类
     * 先遍历connections，向符合条件的人发送信息
     * @param msg 
     */  
    private static void broadcastOneToOne(String msg, String nickName) {  
        String[] arr = msg.split("to");  
        for (ChatAnnotation client : connections) {  
            try {  
                if(arr[1].equals(client.nickname) || nickName.equals(client.nickname)){  
                    synchronized (client) {  
                        client.session.getBasicRemote().sendText(arr[0]+"[私密消息]");  
                    }  
                }  
            } catch (IOException e) {  
                System.out.println("错误:向客户端发送消息失败");  
                connections.remove(client);  
                try {  
                    client.session.close();  
                } catch (IOException e1) {  
                    e1.printStackTrace();  
                }
                String message = String.format("* %s %s", client.nickname,"退出聊天室");  
                broadcast(message);  
            }  
        }  
    }  
    //系统问候语  
    private static void SendHello(String nickName) throws IOException{  
        String m = String.format("* %s %s", nickName, "你好");  
        for (ChatAnnotation client : connections) {  
            if(client.nickname.equals(nickName)){  
                client.session.getBasicRemote().sendText(m);  
            }  
        }  
    }  
    //在线用户  
    private static void onlineList() throws IOException{  
        String online = "";  
        for (ChatAnnotation client : connections) {  
            if(online.equals("")){  
                online = client.nickname;  
            }else{  
                online += ","+client.nickname;  
            }  
        }  
        String m = String.format("* %s,%s", "当前在线用户", online);  
        for (ChatAnnotation client : connections) {  
            client.session.getBasicRemote().sendText(m);  
        }  
    }
}
