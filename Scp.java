/* -*-mode:java; c-basic-offset:2; indent-tabs-mode:nil -*- */
/**
 * This program will demonstrate the file transfer from remote to local
 *   $ CLASSPATH=.:../build javac ScpFrom.java
 *   $ CLASSPATH=.:../build java ScpFrom user@remotehost:file1 file2
 * You will be asked passwd. 
 * If everything works fine, a file 'file1' on 'remotehost' will copied to
 * local 'file1'.
 *
 */
import com.jcraft.jsch.*;
import java.awt.*;
import javax.swing.*;
import java.io.*;

public class Scp{
    static String user;
    static String host;
    static String pass;
    static String rfile;
    static String lfile;
    JSch jsch;
    Session session;

    public Scp(String user, String host,String pass,String rfile,String lfile){
      this.user=user;
      this.host=host;
      this.pass=pass;
      this.rfile=rfile;
      this.lfile=lfile;
      try{
          jsch=new JSch();
          session=jsch.getSession(user, host, 22);
          session.setPassword(pass);

          // username and password will be given via UserInfo interface.
          UserInfo ui=new MyUserInfo();
          session.setUserInfo(ui);
          session.connect();
      }catch(Exception e){

      }
    }

  public void ScpFrom(){
    FileOutputStream fos=null;
    try{

      String prefix=null;
      if(new File(lfile).isDirectory()){
        prefix=lfile+File.separator;
      }

      // exec 'scp -f rfile' remotely
      String command="scp -f "+rfile;
      Channel channel=session.openChannel("exec");
      ((ChannelExec)channel).setCommand(command);

      // get I/O streams for remote scp
      OutputStream out=channel.getOutputStream();
      InputStream in=channel.getInputStream();

      channel.connect();

      byte[] buf=new byte[1024];

      // send '\0'
      buf[0]=0; out.write(buf, 0, 1); out.flush();

      while(true){
    int c=checkAck(in);
        if(c!='C'){
      break;
    }

        // read '0644 '
        in.read(buf, 0, 5);

        long filesize=0L;
        while(true){
          if(in.read(buf, 0, 1)<0){
            // error
            break; 
          }
          if(buf[0]==' ')break;
          filesize=filesize*10L+(long)(buf[0]-'0');
        }

        String file=null;
        for(int i=0;;i++){
          in.read(buf, i, 1);
          if(buf[i]==(byte)0x0a){
            file=new String(buf, 0, i);
            break;
      }
        }

    //System.out.println("filesize="+filesize+", file="+file);

        // send '\0'
        buf[0]=0; out.write(buf, 0, 1); out.flush();

        // read a content of lfile
        fos=new FileOutputStream(prefix==null ? lfile : prefix+file);
        int foo;
        while(true){
          if(buf.length<filesize) foo=buf.length;
          else foo=(int)filesize;
          foo=in.read(buf, 0, foo);
          if(foo<0){
            // error 
            break;
          }
          fos.write(buf, 0, foo);
          filesize-=foo;
          if(filesize==0L) break;
        }
        fos.close();
        fos=null;

        if(checkAck(in)!=0){
            System.exit(0);
        }
        // send '\0'
        buf[0]=0; out.write(buf, 0, 1); out.flush();
      }

      //session.disconnect();

      //System.exit(0);
    }
    catch(Exception e){
      System.out.println(e);
      try{if(fos!=null)fos.close();}catch(Exception ee){}
    }
  }
  

  public void ScpTo(){

    FileInputStream fis=null;
    try{

      

      boolean ptimestamp = false;

      // exec 'scp -t rfile' remotely
      String command="scp " + (ptimestamp ? "-p" :"") +" -t "+rfile;
      Channel channel=session.openChannel("exec");
      ((ChannelExec)channel).setCommand(command);

      // get I/O streams for remote scp
      OutputStream out=channel.getOutputStream();
      InputStream in=channel.getInputStream();

      channel.connect();

      if(checkAck(in)!=0){
        System.exit(0);
      }

      File _lfile = new File(lfile);

      if(ptimestamp){
        command="T"+(_lfile.lastModified()/1000)+" 0";
        // The access time should be sent here,
        // but it is not accessible with JavaAPI ;-<
        command+=(" "+(_lfile.lastModified()/1000)+" 0\n"); 
        out.write(command.getBytes()); out.flush();
        if(checkAck(in)!=0){
      System.exit(0);
        }
      }

      // send "C0644 filesize filename", where filename should not include '/'
      long filesize=_lfile.length();
      command="C0644 "+filesize+" ";
      if(lfile.lastIndexOf('/')>0){
        command+=lfile.substring(lfile.lastIndexOf('/')+1);
      }
      else{
        command+=lfile;
      }
      command+="\n";
      out.write(command.getBytes()); out.flush();
      if(checkAck(in)!=0){
        System.exit(0);
      }

      // send a content of lfile
      fis=new FileInputStream(lfile);
      byte[] buf=new byte[1024];
      while(true){
        int len=fis.read(buf, 0, buf.length);
        if(len<=0) break;
        out.write(buf, 0, len); //out.flush();
      }
      fis.close();
      fis=null;
      // send '\0'
      buf[0]=0; out.write(buf, 0, 1); out.flush();
      if(checkAck(in)!=0){
        System.exit(0);
      }
      out.close();

      channel.disconnect();
      //session.disconnect();

      //System.exit(0);
    }
    catch(Exception e){
      System.out.println(e);
      try{if(fis!=null)fis.close();}catch(Exception ee){}
    }
  }
  void disconnect(){
    try{
        session.disconnect();
    }catch(Exception e){

    }
  }

    

  static int checkAck(InputStream in) throws IOException{
    int b=in.read();
    // b may be 0 for success,
    //          1 for error,
    //          2 for fatal error,
    //          -1
    if(b==0) return b;
    if(b==-1) return b;

    if(b==1 || b==2){
      StringBuffer sb=new StringBuffer();
      int c;
      do {
    c=in.read();
    sb.append((char)c);
      }
      while(c!='\n');
      if(b==1){ // error
    System.out.print(sb.toString());
      }
      if(b==2){ // fatal error
    System.out.print(sb.toString());
      }
    }
    return b;
  }

  public static class MyUserInfo implements UserInfo, UIKeyboardInteractive{
    public String getPassword(){ return passwd; }
    public boolean promptYesNo(String str){
      Object[] options={ "yes", "no" };
      int foo=JOptionPane.showOptionDialog(null, 
             str,
             "Warning", 
             JOptionPane.DEFAULT_OPTION, 
             JOptionPane.WARNING_MESSAGE,
             null, options, options[0]);
       return foo==0;
    }
  
    String passwd;
    JTextField passwordField=(JTextField)new JPasswordField(20);

    public String getPassphrase(){ return null; }
    public boolean promptPassphrase(String message){ return true; }
    public boolean promptPassword(String message){
      Object[] ob={passwordField}; 
      int result=
      JOptionPane.showConfirmDialog(null, ob, message,
                    JOptionPane.OK_CANCEL_OPTION);
      if(result==JOptionPane.OK_OPTION){
    passwd=passwordField.getText();
    return true;
      }
      else{ return false; }
    }
    public void showMessage(String message){
      JOptionPane.showMessageDialog(null, message);
    }
    final GridBagConstraints gbc = 
      new GridBagConstraints(0,0,1,1,1,1,
                             GridBagConstraints.NORTHWEST,
                             GridBagConstraints.NONE,
                             new Insets(0,0,0,0),0,0);
    private Container panel;
    public String[] promptKeyboardInteractive(String destination,
                                              String name,
                                              String instruction,
                                              String[] prompt,
                                              boolean[] echo){
      panel = new JPanel();
      panel.setLayout(new GridBagLayout());

      gbc.weightx = 1.0;
      gbc.gridwidth = GridBagConstraints.REMAINDER;
      gbc.gridx = 0;
      panel.add(new JLabel(instruction), gbc);
      gbc.gridy++;

      gbc.gridwidth = GridBagConstraints.RELATIVE;

      JTextField[] texts=new JTextField[prompt.length];
      for(int i=0; i<prompt.length; i++){
        gbc.fill = GridBagConstraints.NONE;
        gbc.gridx = 0;
        gbc.weightx = 1;
        panel.add(new JLabel(prompt[i]),gbc);

        gbc.gridx = 1;
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weighty = 1;
        if(echo[i]){
          texts[i]=new JTextField(20);
        }
        else{
          texts[i]=new JPasswordField(20);
        }
        panel.add(texts[i], gbc);
        gbc.gridy++;
      }

      if(JOptionPane.showConfirmDialog(null, panel, 
                                       destination+": "+name,
                                       JOptionPane.OK_CANCEL_OPTION,
                                       JOptionPane.QUESTION_MESSAGE)
         ==JOptionPane.OK_OPTION){
        String[] response=new String[prompt.length];
        for(int i=0; i<prompt.length; i++){
          response[i]=texts[i].getText();
        }
    return response;
      }
      else{
        return null;  // cancel
      }
    }
  }
}