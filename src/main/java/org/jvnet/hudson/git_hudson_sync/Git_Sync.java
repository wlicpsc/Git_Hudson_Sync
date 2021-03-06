package org.jvnet.hudson.git_hudson_sync;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLDecoder;
import java.util.Date;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpException;
import org.apache.commons.httpclient.UsernamePasswordCredentials;
import org.apache.commons.httpclient.auth.AuthScope;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.commons.httpclient.methods.StringRequestEntity;
import org.dom4j.Document;
import org.dom4j.Element;
import org.dom4j.io.SAXReader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.ListBranchCommand;
import org.eclipse.jgit.api.ListBranchCommand.ListMode;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RepositoryBuilder;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class Git_Sync {
	private static URL url;
	private static String username;
	private static String password;
	private static String host;
	private static int port;
	/**
	 * @param args
	 * @throws Exception 
	 */
	public static void main(String[] args) throws Exception {
		// TODO Auto-generated method stub
		if(args.length!=3)
		{
			System.out.println("Usage: java <class> <base job> <git repo path> <template file name>");
			System.out.println("If the template file does not exist, it will use ./configTemplate.xml instead.");
		}
		else
			try {
				syncHudsonJobs(args[0], args[1], args[2]);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	}
	
	@SuppressWarnings("unchecked")
	public static void syncHudsonJobs(String urlAddr, String gitDir, String templateFile) throws IOException, Exception
	{
		url = urlAddr==null?new URL("http://127.0.0.1:8081/hudson"):new URL(urlAddr);
		host=url.getHost();
		System.out.println(host);
		port=url.getPort()!=0?url.getPort():80;
		System.out.println(port);
        String userInfo = url.getUserInfo();
        if (userInfo != null && userInfo.length() > 0) 
        {
        	int colon = userInfo.indexOf(':');
        	if (colon == -1) 
        	{
        		username = URLDecoder.decode(userInfo, "UTF-8");
        	}
        	else
        	{
        		username = URLDecoder.decode(userInfo.substring(0, colon), "UTF-8");
        		password = URLDecoder.decode(userInfo.substring(colon + 1), "UTF-8");
        	}
        }
        System.out.println(username+", "+password);
        HttpClient httpclient = new HttpClient();
		httpclient.getState().setCredentials(new AuthScope(host, port, "realm"), new UsernamePasswordCredentials(username, password));
		httpclient.getParams().setAuthenticationPreemptive(true);
		Document dom = null;
        PostMethod post=new PostMethod(url+"/api/xml");
        post.setDoAuthentication(true);
        try
        {
        	httpclient.executeMethod(post);
        	dom = new SAXReader().read(post.getResponseBodyAsStream());
        } catch (HttpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			post.releaseConnection();
		}
		File file=gitDir==null?new File("/Users/wlicpsc/Documents/Projects/Kashoo/books/.git"):new File(gitDir).getAbsoluteFile();
		File template=new File(templateFile);
		RepositoryBuilder builder=new RepositoryBuilder();
		Repository repo;
		repo=builder.setGitDir(file).readEnvironment().findGitDir().build();
		Git git=new Git(repo);
		ListBranchCommand lbCommand=git.branchList();
		lbCommand.setListMode(ListMode.REMOTE);
		List<Ref> branchList=lbCommand.call();
		if(branchList.isEmpty()) {
			System.out.println("No branches found in "+file);
		}
		RevWalk walk=new RevWalk(repo);
		Date now=new Date();
		Pattern pattern=Pattern.compile("#[0-9]+.*");
		Matcher gitMatcher;
		String branchNumber = null;
		for(Ref item:branchList)
		{
			System.out.println(item.getName());
			System.out.println(item.toString()+", "+item.getObjectId().name());
			gitMatcher=pattern.matcher(item.getName());
			while(gitMatcher.find())
			{
				System.out.println(gitMatcher.group(0));
				branchNumber=gitMatcher.group(0).substring(1);
			}
			RevCommit commit=walk.parseCommit(item.getObjectId());
			Date commitTime=new Date((long)commit.getCommitTime()*1000);
			System.out.println(commitTime+", "+((now.getTime()-commitTime.getTime())/(24*60*60*1000)>30?"Old":"Recent"));
			if((now.getTime()-commitTime.getTime())/(24*60*60*1000)>30)	//if the date is not within 30 days
			{
		        for( Element job : (List<Element>)dom.getRootElement().elements("job")) {
		            System.out.println(job.elementText("name"));
		            if(branchNumber.equals(job.elementText("name")))
		            {
		            	//delete the job
		            	deleteJob(branchNumber);
		            }
		        }
			}
			else
			{
				boolean exists=false;
				for( Element job : (List<Element>)dom.getRootElement().elements("job"))
				{
					if(branchNumber.equals(job.elementText("name")))
					{
						exists=true;
						break;
					}
				}
				if(exists==false)
					addJob(template.exists()?template:null, branchNumber);
			}
		}
	}
	
	public static String generateConfigFromTemplate(File file, String branchId) throws UnsupportedEncodingException
	{
		if(file==null)
			file=new File("configTemplate.xml");
		String newFileText;
		try
		{
		FileInputStream fis=new FileInputStream(file);
		BufferedReader in = new BufferedReader(new InputStreamReader(fis));
		String line="", templateText="";
		while((line=in.readLine())!=null)
		{
			templateText+=line+"\r\n";				
		}
		in.close();
		newFileText=templateText.replaceFirst("<name>\\*\\*</name>", "<name>#"+branchId+"</name>");
		System.out.println("Success changed and written the file");
		}
		catch(Exception e)
		{
			e.printStackTrace();
			return "";
		}
		return newFileText;
	}
	
	public static boolean addJob(File file, String branchNumber)
	{
		boolean outcome = false;
		System.out.println(url+"/createItem?name="+branchNumber);
		PostMethod post=new PostMethod(url+"/createItem?name="+branchNumber);
		HttpClient httpclient = new HttpClient();
		httpclient.getState().setCredentials(new AuthScope(host, port, "realm"), new UsernamePasswordCredentials(username, password));
		httpclient.getParams().setAuthenticationPreemptive(true);
		try
		{
			post.setDoAuthentication(true);
			post.setRequestEntity(new StringRequestEntity(generateConfigFromTemplate(file, branchNumber), "text/xml", "UTF-8"));
			int result=httpclient.executeMethod(post);
			System.out.println("Response status code: " + result);
			System.out.println("Response body: ");
            System.out.println(post.getResponseBodyAsString());
            if(result<400)
            	outcome=true;
		} catch (HttpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			post.releaseConnection();
		}
		if(outcome==false)
			System.out.println("Error adding the job #"+branchNumber);
		return outcome;
	}
	
	public static boolean deleteJob(String branchNumber)
	{
		boolean outcome = false;
		PostMethod post=new PostMethod(url+"/job/"+branchNumber+"/doDelete");
		HttpClient httpclient = new HttpClient();
		httpclient.getState().setCredentials(new AuthScope(host, port, "realm"), new UsernamePasswordCredentials(username, password));
		httpclient.getParams().setAuthenticationPreemptive(true);
		try
		{
			post.setDoAuthentication(true);
			int result=httpclient.executeMethod(post);
			System.out.println("Response status code: " + result);
			System.out.println("Response body: ");
            System.out.println(post.getResponseBodyAsString());
            if(result<400)
            	outcome=true;
		} catch (HttpException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		finally
		{
			post.releaseConnection();
		}
		if(outcome==false)
			System.out.println("Failed to delete job #"+branchNumber);
		return outcome;
	}

}
