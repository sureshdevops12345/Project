import com.gsihealth.jenkins.Common
import com.gsihealth.jenkins.pojo.GsiJob
import com.gsihealth.jenkins.pojo.GsiRun
import com.gsihealth.jenkins.runner.GitBuildTask
import com.gsihealth.jenkins.utils.CommonUtils
import com.gsihealth.jenkins.utils.EmailListBuilder
import com.gsihealth.jenkins.utils.GitBuildSummary
import com.gsihealth.jenkins.utils.Logger

def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def failure = false
	def spring = config.Tests
     def date="";
	 def path="";
	 def time="";
	 def rollbackScriptsPath="";
	 def jobPath="";
    node {
GitBuildTask task = new GitBuildTask(env)
        task.setConfig(config)
        task.startDate = new Date()
        try{
		
		
		//check job name
		def jobnameScriptPath="D:\\Sadha\\jobName.txt"
		                 File InfoFile = new File("${jobnameScriptPath}")
                         if( !InfoFile.exists() ) {
						  println "${jobnameScriptPath}"
                           println "File does not exist"
						   error("Build failed....")
                         } else {
						 if(InfoFile.text){ 
                               if(InfoFile.text){
                 def finalDate=InfoFile.text
				  jobPath=finalDate.trim()
				   println "${jobPath}"
				  
				}
                }
             }
		
		
		
		//date File 
                def rollbackScriptPath="D:\\Jenkins\\workspace\\${jobPath}\\daily-monitoring-interface-scripts\\src\\output\\Outbound_notification\\TestCaseName\\OutboundNotification_Test.txt"
		                 File theInfoFile = new File("${rollbackScriptPath}")
                         if( !theInfoFile.exists() ) {
						  println "${rollbackScriptPath}"
                           println "File does not exist"
						   error("Build failed....")
                         } else {
						 if(theInfoFile.text){ 
                               if(theInfoFile.text){
                 def finalDate=theInfoFile.text
				  path=finalDate.trim()
				   println "${path}"
				  
				}
                }
             }
		
		
		//date File
if("${path}"=="OutboundNotification_Test")	{	

                 rollbackScriptsPath="D:\\Jenkins\\workspace\\${jobPath}\\daily-monitoring-interface-scripts\\src\\output\\Outbound_notification\\logChecktime\\OutboundNotification_Test.txt"
 println "${rollbackScriptsPath}"
				 }else {
                  rollbackScriptsPath="D:\\Jenkins\\workspace\\${jobPath}\\daily-monitoring-interface-scripts\\src\\output\\Inbound_alerts\\logChecktime\\Inboundalerts_Test.txt"
println "${rollbackScriptsPath}"
				  }		          
				  File theInfoFiles = new File("${rollbackScriptsPath}")
				  
                         if( !theInfoFiles.exists() ) {
						 println "${rollbackScriptsPath}"
                           println "File does not exist"
						   error("Build failed....")
                         } else {
						 if(theInfoFiles.text){ 
                               if(theInfoFiles.text){
                 def finalDate=theInfoFiles.text
				  date=finalDate.trim()
				   println "${date}"
				   time=  date.split('-')
				  
				}
                }
             }
          //def now = new Date()
         // println now.format("MM/dd/YYYY HH:mm:ss", TimeZone.getTimeZone('EST'))
            stage('Get Logs'){
            //calling restart groovy
                def PS_deploy = "D:/jenkins_utils/deployment_v2/ps/GetServerLogs.ps1 -env ${config.env} -environmentType ${config.environmentType} -server ${config.server} -date ${time[0]} -end ${time[1]}"
                bat script: "${getPSCmd(PS_deploy)}"
				}
			
				
        }catch(e){
            print e
            echo "[BUILD END]"
            failure = true
        }
   
   					
    
		
    }
 

}
@NonCPS
def notifyFailed() {
mail (to: 'sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com,prabhaharan.velu@gsihealth.com',
subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) has failed....",
body: "Job Failed ${env.BUILD_URL}.");

}
@NonCPS
def notifyPassed() {
mail (to: 'sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com,prabhaharan.velu@gsihealth.com',
//mail (to: 'ContinuousDelivery@gsihealth.com',
 subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) has passed....",
 body: "Job passed ${env.BUILD_URL}.");
}

@NonCPS
def String getPSCmd(command){
    def prefix = "powershell.exe -command \""
    def suffix = "\";exit \$LASTEXITCODE;"
    return prefix+command+suffix
}

@NonCPS
def getJobPath(rootPath, job_name) {
    def arr = job_name.split("/")
    for (int i = 0; i < arr.length; i++) {
        rootPath += "\\jobs\\${arr[i]}"
    }
    return rootPath
}


@NonCPS
def String replaceUrl( text, text2) {
    return text.replaceAll("Jenkins01:8989", text2)
}
@NonCPS
def String replaceAll( text, regex, newText) {
    return text.replaceAll(regex, newText)
}