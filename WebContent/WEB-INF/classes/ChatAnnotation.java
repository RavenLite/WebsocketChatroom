

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
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


@ServerEndpoint(value = "/websocket/chat")

public class ChatAnnotation {

  
    private static final String GUEST_PREFIX = "Guest";//用户名字前缀
    private static final AtomicInteger connectionIds = new AtomicInteger(0);//计数器，用来分配用户序号  atomicinteger， 无锁整数，线程安全，多用于高并发
    private static final Set<ChatAnnotation> connections =
            new CopyOnWriteArraySet<ChatAnnotation>();//连接的用户   set集合

    private final String nickname;//用户名
    private Session session;//会话

    /* 构造器，分配用户名给当前用户。    默认前缀Guest  +   用户进入次序*/
    public ChatAnnotation() {
        nickname = GUEST_PREFIX + connectionIds.getAndIncrement();
    }

    //开启一个新的会话
    @OnOpen
    public void start(Session session) {
        this.session = session;
        connections.add(this);//将这一会话加入到连接集合set里
        String message = String.format("* %s %s", nickname, "has joined.");
        broadcast(message);
    }

    //终止会话
    @OnClose
    public void end() {
        connections.remove(this);
        String message = String.format("* %s %s",
                nickname, "has disconnected.");
        broadcast(message);
    }

    //发送消息，先过滤，将过滤后的消息发送出去
    @OnMessage
    public void incoming(String message) {
        // Never trust the client
    	/* 过滤过后的信息*/
        String filteredMessage = String.format("%s: %s",
                nickname, HTMLFilter.filter(message.toString()));
        broadcast(filteredMessage);
    }



    /* 报错，将IO错误主机删除 */
    @OnError
    public void onError(Throwable t) throws Throwable {
      
    }


    private static void broadcast(String msg) {
        for (ChatAnnotation client : connections) {
            try {
                synchronized (client) {
                    client.session.getBasicRemote().sendText(msg);
                }
            } catch (IOException e) {
              
                connections.remove(client);
                try {
                    client.session.close();
                } catch (IOException e1) {
                    // Ignore
                }
                String message = String.format("* %s %s",
                        client.nickname, "has been disconnected.");
                broadcast(message);
            }
        }
    }
}
