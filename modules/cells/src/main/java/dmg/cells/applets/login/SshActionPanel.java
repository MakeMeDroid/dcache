package dmg.cells.applets.login ;

import java.applet.*;
import java.awt.* ;
import java.awt.event.* ;

public class SshActionPanel extends Panel {

     private static final long serialVersionUID = -2434057298152104961L;
     private ActionListener _actionListener ;
     
     public void addActionListener( ActionListener listener ){
       _actionListener = listener ;
     }
     protected void informActionListeners(String msg ){
        if( _actionListener != null ) {
            _actionListener.actionPerformed(
                    new ActionEvent(this, 0, msg));
        }
     }



}
