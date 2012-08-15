// generated by GLUE/wsdl2java on Thu Jan 13 14:50:37 CST 2005
package diskCacheV111.srm;

public class StorageElementInfo implements java.io.Serializable
{
  private static final long serialVersionUID = -8646194589679886805L;
  public long totalSpace;
  public long usedSpace;
  public long availableSpace;
    
  public String toString() {
        return "StorageElementInfo :"+
             "\n                     totalSpace     ="+totalSpace+" ("+
                (totalSpace>>10)+" KB)"+
             "\n                     usedSpace      ="+usedSpace+" ("+
                (usedSpace>>10)+" KB)"+
             "\n                     availableSpace ="+availableSpace+" ("+
                (availableSpace>>10)+" KB)";
  }
}
