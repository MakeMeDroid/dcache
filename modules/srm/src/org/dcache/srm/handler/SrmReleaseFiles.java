/*
 * SrmLs.java
 *
 * Created on October 4, 2005, 3:40 PM
 */

package org.dcache.srm.handler;

import org.dcache.srm.v2_2.TReturnStatus;
import org.dcache.srm.v2_2.TStatusCode;
import org.dcache.srm.v2_2.TSURLReturnStatus;
import org.dcache.srm.v2_2.SrmReleaseFilesRequest;
import org.dcache.srm.v2_2.SrmReleaseFilesResponse;
import org.dcache.srm.SRMUser;
import org.dcache.srm.request.RequestCredential;
import org.dcache.srm.AbstractStorageElement;
import org.dcache.srm.SRMException;
import org.dcache.srm.scheduler.Job;
import org.dcache.srm.scheduler.Scheduler;
import org.dcache.srm.request.ContainerRequest;
import org.dcache.srm.request.FileRequest;
import org.dcache.srm.request.GetRequest;
import org.dcache.srm.request.GetFileRequest;
import org.dcache.srm.request.BringOnlineRequest;
import org.dcache.srm.request.BringOnlineFileRequest;
import org.dcache.srm.util.Configuration;
import org.dcache.srm.scheduler.State;
import org.dcache.srm.scheduler.IllegalStateTransition;
import org.dcache.srm.v2_2.ArrayOfTSURLReturnStatus;
import org.apache.axis.types.URI; 
import org.apache.log4j.Logger;
import org.dcache.srm.SRM;
import java.util.Set;
import java.util.HashSet;
import java.util.Map;
import java.util.HashMap;
import org.dcache.srm.request.sql.DatabaseFileRequestStorage;


/**
 *
 * @author  timur
 */
public class SrmReleaseFiles {
    
    
    private static final Logger logger= 
        Logger.getLogger(SrmReleaseFiles.class.getName()) ;
    
    private final static String SFN_STRING="?SFN=";
    AbstractStorageElement storage;
    SrmReleaseFilesRequest srmReleaseFilesRequest;
    SrmReleaseFilesResponse response;
    Scheduler getScheduler;
    SRMUser user;
    RequestCredential credential;
    Configuration configuration;
    private int results_num;
    private int max_results_num;
    int numOfLevels =0;
    SRM srm;
    
    /** Creates a new instance of SrmReleaseFiles */
    public SrmReleaseFiles(SRMUser user,
            RequestCredential credential,
            SrmReleaseFilesRequest srmReleaseFilesRequest,
            AbstractStorageElement storage,
            SRM srm,
            String client_host) {
        logger.info("SrmReleaseFiles user="+user);
        this.srmReleaseFilesRequest = srmReleaseFilesRequest;
        this.user = user;
        this.credential = credential;
        this.storage = storage;
        this.getScheduler = srm.getGetRequestScheduler();
        this.configuration = srm.getConfiguration();
        this.srm = srm;
    }
    
    private void say(String words_of_wisdom) {
        if(storage!=null) {
            storage.log("SrmReleaseFiles "+words_of_wisdom);
        }
    }
    
    private void esay(String words_of_despare) {
        if(storage!=null) {
            storage.elog("SrmReleaseFiles "+words_of_despare);
        }
    }
    private void esay(Throwable t) {
        if(storage!=null) {
            storage.elog(" SrmReleaseFiles exception : ");
            storage.elog(t);
        }
    }
    boolean longFormat =false;
    String servicePathAndSFNPart = "";
    int port;
    String host;
    public SrmReleaseFilesResponse getResponse() {
        if(response != null ) return response;
        try {
            response = srmReleaseFiles();
        } catch(Exception e) {
            storage.elog(e);
            response = getFailedResponse(e.toString());
        }
        
        return response;
    }
    
    public static final SrmReleaseFilesResponse getFailedResponse(String error) {
        return getFailedResponse(error,null);
    }
    
    public static final SrmReleaseFilesResponse getFailedResponse(String error,TStatusCode statusCode) {
        if(statusCode == null) {
            statusCode =TStatusCode.SRM_FAILURE;
        }
        logger.error("getFailedResponse: "+error+" StatusCode "+statusCode);
 
        TReturnStatus status = new TReturnStatus();
        status.setStatusCode(statusCode);
        status.setExplanation(error);
        SrmReleaseFilesResponse srmReleaseFilesResponse = new SrmReleaseFilesResponse();
        srmReleaseFilesResponse.setReturnStatus(status);
        return srmReleaseFilesResponse;
    }
    /**
     * implementation of srm ls
     */
    public SrmReleaseFilesResponse srmReleaseFiles()
    throws SRMException,org.apache.axis.types.URI.MalformedURIException,
            java.sql.SQLException, IllegalStateTransition {
        
        
        say("Entering srmReleaseFiles");
        String requestToken = srmReleaseFilesRequest.getRequestToken();
        Long requestId = null;
        if( requestToken != null ) {        
            try {
                requestId = new Long( requestToken);
            } 
            catch (NumberFormatException nfe){
                return getFailedResponse(" requestToken \""+
                                         requestToken+"\"is not valid",
                                         TStatusCode.SRM_INVALID_REQUEST);
            }
        }        
        say("srmReleaseFiles, requestToken="+requestToken);
        URI [] surls ;
        if(  srmReleaseFilesRequest.getArrayOfSURLs() == null ){
            if(requestToken == null) {
                return getFailedResponse(
                        "request contains no request token and no SURLs",
                        TStatusCode.SRM_NOT_SUPPORTED);
            }
            surls = null;
        }  
	else if (requestToken == null) {
            surls = srmReleaseFilesRequest.getArrayOfSURLs().getUrlArray();
            
            return unpinDirectlyBySURLs(surls);
            
        }
        else {
            surls = srmReleaseFilesRequest.getArrayOfSURLs().getUrlArray();
        }
        
        ContainerRequest request =(ContainerRequest) ContainerRequest.getRequest(requestId);
        if(request == null) {
            if(surls != null && surls.length > 0) {
                return unpinFilesDirectlyBySURLAndRequestId(requestId, surls);
               
            } else {
                return getFailedResponse("request for requestToken \""+
                                         requestToken+"\"is not found",
                                         TStatusCode.SRM_INVALID_REQUEST);
            }
            
        }
        if ( !(request instanceof GetRequest || request instanceof BringOnlineRequest) ){
            return getFailedResponse("request for requestToken \""+
				     requestToken+"\"is not srmPrepareToGet or srmBringOnlineRequest request",
				     TStatusCode.SRM_INVALID_REQUEST);
        }
        
        //if(request instanceof GetRequest) {
        String surl_strings[] = null;
        if( surls == null ){
        say( "surls == null");
            if(request instanceof GetRequest) {
                synchronized(request) {
                    State state = request.getState();
                    if(!State.isFinalState(state)) {
                        request.setState(State.DONE,"SrmReleaseFiles called");
                    }
                }
            } else {
                BringOnlineRequest bringOnlineRequest = (BringOnlineRequest)request;
                return bringOnlineRequest.releaseFiles(null,null);
            }
        } else {
         say( "surls != null");
           if(surls.length == 0) {
                return getFailedResponse("0 lenght SiteURLs array");
            }
            surl_strings = new String[surls.length];
            for(int i = 0; i< surls.length; ++i) {
                if(surls[i] == null) {
                    return getFailedResponse("surls["+i+"]=null");
                }
                surl_strings[i] = surls[i].toString();
            }
            if(request instanceof GetRequest) {
                for(int i = 0; i< surls.length; ++i) {
                    FileRequest fileRequest = request.getFileRequestBySurl(surl_strings[i]);
                    synchronized(fileRequest) {
                        State state = fileRequest.getState();
                        if(!State.isFinalState(state)) {
                            fileRequest.setState(State.DONE,"SrmReleaseFiles called");
                        }
                    }
                }
            } else {
                BringOnlineRequest bringOnlineRequest = (BringOnlineRequest)request;
                return bringOnlineRequest.releaseFiles(surls,surl_strings);
                
            }
        }
        
        TReturnStatus status = new TReturnStatus();
        status.setStatusCode(TStatusCode.SRM_SUCCESS);
        SrmReleaseFilesResponse srmReleaseFilesResponse = new SrmReleaseFilesResponse();
        srmReleaseFilesResponse.setReturnStatus(status);
        if( surls != null) {
            TSURLReturnStatus[] surlReturnStatusArray =  request.getArrayOfTSURLReturnStatus(surl_strings);
            for (TSURLReturnStatus surlReturnStatus:surlReturnStatusArray) {
                if(surlReturnStatus.getStatus().getStatusCode() == TStatusCode.SRM_RELEASED) {
                    surlReturnStatus.getStatus().setStatusCode(TStatusCode.SRM_SUCCESS);
                }
            }
            srmReleaseFilesResponse.setArrayOfFileStatuses(
                    new ArrayOfTSURLReturnStatus(surlReturnStatusArray));
        }
        // we do this to make the srm update the status of the request if it changed
        request.getTReturnStatus();
        return srmReleaseFilesResponse;
        
    }

    private SrmReleaseFilesResponse unpinFilesDirectlyBySURLAndRequestId(final Long requestId, final URI[] surls) {
        say("unpinFilesDirectlyBySURLAndRequestId");
        TSURLReturnStatus[] surlReturnStatusArray = 
            new TSURLReturnStatus[surls.length];
        int failure_num=0;
        for (int i = 0; i< surls.length; ++i) {
           URI surl =  surls[i];
           surlReturnStatusArray[i] = new TSURLReturnStatus();
           surlReturnStatusArray[i].setSurl(surl);
            try {
                BringOnlineFileRequest.unpinBySURLandRequestId(storage,
                    user,requestId,surl.toString());
                surlReturnStatusArray[i].setStatus(
                    new TReturnStatus(TStatusCode.SRM_SUCCESS,"released"));
            }
            catch(Exception e) {
                surlReturnStatusArray[i].setStatus(
                    new TReturnStatus(TStatusCode.SRM_FAILURE,"release failed: "+e));
                failure_num++;
                
            }
        }
        
        TReturnStatus status = new TReturnStatus();
        if(failure_num == 0) {
            status.setStatusCode(TStatusCode.SRM_SUCCESS);
        } else if(failure_num < surls.length) {
            status.setStatusCode(TStatusCode.SRM_PARTIAL_SUCCESS);
        } else {
            status.setStatusCode(TStatusCode.SRM_FAILURE);
        }
        SrmReleaseFilesResponse srmReleaseFilesResponse = new SrmReleaseFilesResponse();
        srmReleaseFilesResponse.setReturnStatus(status);
        srmReleaseFilesResponse.setArrayOfFileStatuses(
                                new ArrayOfTSURLReturnStatus(surlReturnStatusArray));
        return srmReleaseFilesResponse;
    }
    
   private SrmReleaseFilesResponse unpinDirectlyBySURLs(final URI[] surls) throws
   java.sql.SQLException, IllegalStateTransition {
       say("unpinDirectlyBySURLs"); 
       //prepare initial return statuses
       Map<URI,TSURLReturnStatus> surlsMap = 
           new HashMap<URI,TSURLReturnStatus>();
        for(URI surl: surls) {
            TSURLReturnStatus rs = new TSURLReturnStatus();
            rs.setSurl(surl);
            rs.setStatus(
                new TReturnStatus(TStatusCode.SRM_INTERNAL_ERROR,"not released"));
            surlsMap.put(surl,rs);
        }
        
        //try to find and relelease active get and bring online file requests 
        releaseFileRequestsDirectlyBySURLs(surls,surlsMap);
        
        //try to unpin surls directly in the pin manager too
        unpinFilesDirectlyBySURLs(surls,surlsMap);
        
        //analize the results to form SrmReleaseFilesResponse
        int failure_num=0;
        TSURLReturnStatus[] surlReturnStatusArray = 
            new TSURLReturnStatus[surls.length];
        for(int i = 0; i<surls.length; i++) {
            URI surl =  surls[i];
            TSURLReturnStatus rs = surlsMap.get(surl);
            if(!rs.getStatus().getStatusCode().equals(TStatusCode.SRM_SUCCESS)) {
                failure_num++;
            }
            surlReturnStatusArray[i] = rs;
        }
        
        TReturnStatus status = new TReturnStatus();
        if(failure_num == 0) {
            status.setStatusCode(TStatusCode.SRM_SUCCESS);
        } else if(failure_num < surls.length) {
            status.setStatusCode(TStatusCode.SRM_PARTIAL_SUCCESS);
        } else {
            status.setStatusCode(TStatusCode.SRM_FAILURE);
        }
        SrmReleaseFilesResponse srmReleaseFilesResponse = new SrmReleaseFilesResponse();
        srmReleaseFilesResponse.setReturnStatus(status);
        srmReleaseFilesResponse.setArrayOfFileStatuses(
                                new ArrayOfTSURLReturnStatus(surlReturnStatusArray));
        return srmReleaseFilesResponse;
     
    }
   
    private void unpinFilesDirectlyBySURLs(final URI[] surls, Map<URI,TSURLReturnStatus> surlsMap ) {
        say("unpinFilesDirectlyBySURLAndRequestId");
        TSURLReturnStatus[] surlReturnStatusArray = 
            new TSURLReturnStatus[surls.length];
        int failure_num=0;
        for (URI surl:surls) {
           TSURLReturnStatus rs=surlsMap.get(surl);
            try {
                BringOnlineFileRequest.unpinBySURL(storage,
                    user,surl.toString());
                rs.setStatus(
                    new TReturnStatus(TStatusCode.SRM_SUCCESS,"released"));
            }
            catch(Exception e) {
                
                esay(e);
                //if rs status is TStatusCode.SRM_INTERNAL_ERROR 
                // this means it was not changed since 
                // it is set initially in the calling function
                // if it chanded, we do not want to override it 
                // as other method of releasing might have already 
                // succeded
            
                if(rs.getStatus().getStatusCode().equals(TStatusCode.SRM_INTERNAL_ERROR)) {
                    rs.setStatus(
                        new TReturnStatus(TStatusCode.SRM_FAILURE,"release failed: "+e));
                }
            }
        }
        
    }

    private void releaseFileRequestsDirectlyBySURLs(final URI[] surls,
        Map<URI,TSURLReturnStatus> surlsMap) throws java.sql.SQLException, IllegalStateTransition {
        Set<BringOnlineFileRequest> bofrsToRelease = 
            findBringOnlineFileRequestBySURLs(surls);
       for (BringOnlineFileRequest fileRequest: bofrsToRelease) {
            
            TSURLReturnStatus release_rs = fileRequest.releaseFile();
            if(release_rs.getStatus().getStatusCode().equals(TStatusCode.SRM_SUCCESS) ) {
                surlsMap.put(release_rs.getSurl(),release_rs);
            } else {
                TSURLReturnStatus rs = surlsMap.get(release_rs.getSurl());
                
                //if rs status is TStatusCode.SRM_INTERNAL_ERROR 
                // this means it was not changed since 
                // it is set initially in the calling function
                // if it chanded, we do not want to override it 
                // as other method of releasing might have already 
                // succeded
            
                if(rs.getStatus().getStatusCode().equals(TStatusCode.SRM_INTERNAL_ERROR)) {
                    surlsMap.put(release_rs.getSurl(),release_rs);
                }
                
            }
        }
       
       Set<GetFileRequest> gfrToRelease = 
            findGetFileRequestBySURLs(surls);
       say("got GetFiles to release:");
       for (GetFileRequest fileRequest: gfrToRelease) {
            
            say("got GetFiles to release: "+fileRequest);
            synchronized(fileRequest) {
                State state = fileRequest.getState();
                if(!State.isFinalState(state)) {
                    fileRequest.setState(State.DONE,"SrmReleaseFiles called");
                }
            }
            TSURLReturnStatus surlReturnStatus =  fileRequest.getTSURLReturnStatus();
            if(surlReturnStatus.getStatus().getStatusCode() == TStatusCode.SRM_RELEASED) {
                surlReturnStatus.getStatus().setStatusCode(TStatusCode.SRM_SUCCESS);
                surlsMap.put(surlReturnStatus.getSurl(),surlReturnStatus);
            } else {
                TSURLReturnStatus rs = surlsMap.get(surlReturnStatus.getSurl());
                
                //if rs status is TStatusCode.SRM_INTERNAL_ERROR 
                // this means it was not changed since 
                // it is set initially in the calling function
                // if it chanded, we do not want to override it 
                // as other method of releasing might have already 
                // succeded
            
                if(rs.getStatus().getStatusCode().equals(TStatusCode.SRM_INTERNAL_ERROR)) {
                    surlsMap.put(surlReturnStatus.getSurl(),surlReturnStatus);
                }
                
            }
        }
            
    }
    
    private Set<BringOnlineFileRequest> findBringOnlineFileRequestBySURLs(URI[] surls)  {
        Scheduler scheduler = srm.getBringOnlineRequestScheduler();
        
        DatabaseFileRequestStorage reqstorage = srm.getBringOnlineFileRequestStorage();
        Set<BringOnlineFileRequest> foundRequests = 
            new HashSet<BringOnlineFileRequest>();
        Set<Long> activeRequestIds ;
        try {
            activeRequestIds = 
                reqstorage.getActiveFileRequestIds(scheduler.getId());
        } catch (java.sql.SQLException sqle) {
            esay(sqle);
            //just return empty
            return foundRequests;
        }
        
        for(Long requestId:activeRequestIds) {
            Job job = Job.getJob(requestId);
            if(job == null || !(job instanceof BringOnlineFileRequest)) {
                continue;
            }
            BringOnlineFileRequest bofr = (BringOnlineFileRequest)job;
            for(URI surl: surls) { 
                if(bofr.getSurlString().equals(surl.toString())) {
                    foundRequests.add(bofr);
                }
            }
        }
        return foundRequests;
    }
    
    private Set<GetFileRequest> findGetFileRequestBySURLs(URI[] surls)  {
        Scheduler scheduler = srm.getGetRequestScheduler();
        DatabaseFileRequestStorage reqstorage = srm.getGetFileRequestStorage();
        Set<GetFileRequest> foundRequests = 
            new HashSet<GetFileRequest>();
        Set<Long> activeRequestIds ;
        try {
            activeRequestIds = 
                reqstorage.getActiveFileRequestIds(scheduler.getId());
        } catch (java.sql.SQLException sqle) {
            esay(sqle);
            //just return empty
            return foundRequests;
        }
        
        
        for(Long requestId:activeRequestIds) {
            Job job = Job.getJob(requestId);
            if(job == null || !(job instanceof GetFileRequest)) {
                continue;
            }
            GetFileRequest gfr = (GetFileRequest)job;
            for(URI surl: surls) { 
                if(gfr.getSurlString().equals(surl.toString())) {
                    foundRequests.add(gfr);
                }
            }
        }
        return foundRequests;
    }

    
}
