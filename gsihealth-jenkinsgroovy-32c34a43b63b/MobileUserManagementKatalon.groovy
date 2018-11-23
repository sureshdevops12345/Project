import com.gsihealth.jenkins.Common
import com.gsihealth.jenkins.pojo.GsiJob
import com.gsihealth.jenkins.pojo.GsiRun
import com.gsihealth.jenkins.runner.GitBuildTask
import com.gsihealth.jenkins.utils.CommonUtils
import com.gsihealth.jenkins.utils.EmailListBuilder
import com.gsihealth.jenkins.utils.GitBuildSummary
import com.gsihealth.jenkins.utils.Logger

def call(body) {


    Logger logger = new Logger()

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def failure = false
	def status = false
	def testSuitePathLower 
	def testSuiteCollectionPath	
	 testSuiteCollectionPath = config.testSuiteCollectionPath
	 
	print "${testSuiteCollectionPath}".length()
	def testSuitePath = config.testSuitePath
	print "${testSuitePath}".length()
	def runAt=config.runAt
	if(runAt.equals("local")){
	 testSuitePathLower = "Chrome (headless)"
	}else{
	testSuitePathLower=testSuitePath.toLowerCase()
	}
	 
	 
	 
    print "${testSuitePathLower}"
    node {

        GitBuildTask task = new GitBuildTask(env)
        task.setConfig(config)
        task.startDate = new Date()

        stage('Checkout from BitBucket'){
            //Marks build start
            logger.info("[BUILD START]")

            //Checkout the latest script
            dir("./${config.KatalonRep}") {
                def checkoutResult = git credentialsId: '60c19ca5-84bb-4508-a665-0c7895d3031b', url: "https://sadhasivim@bitbucket.org/gsihealth/${config.KatalonRep}.git", changeLog:true
                task.checkoutResult = checkoutResult
                sleep(10)
                bat 'cd..'
            }
        }

          stage('Test Execution'){
		  def katalonCmd
		  catchError {
          try{
		   dir("./Katalon_Studio_Windows_64-5.0.1.0") {
		   if("${testSuiteCollectionPath}".length() >0){
		    katalonCmd = "katalon -runMode=console -projectPath=\"D:/Jenkins/workspace/${config.jobName}/UserManagementToolDailyAutomationReports/UserManagement.prj\" -reportFolder=\"D:/Jenkins/workspace/${config.jobName}/Katalon_Studio_Windows_64-5.0.1.0/Reports\" -reportFileName=\"report\" -retry=0 -testSuiteCollectionPath=\"Test Suites/${config.testSuiteCollectionPath}\""
		   }
		   else if("${testSuitePath}".length() >0){
		    katalonCmd = "katalon -runMode=console -projectPath=\"D:/Jenkins/workspace/${config.jobName}/UserManagementToolDailyAutomationReports/UserManagement.prj\" -reportFolder=\"D:/Jenkins/workspace/${config.jobName}/Katalon_Studio_Windows_64-5.0.1.0/Reports\" -reportFileName=\"report\" -retry=0 -testSuitePath=\"Test Suites/${config.testSuitePath}\" -browserType=\"${config.browser}\""
		  }
          else{
		  logger.info("configuration error. please check the configuration")
		    error("Build failed....")
			status = false
           }		   
			  logger.info("${katalonCmd}")
		      bat "${katalonCmd}"
			  status = true
			 }
           }catch(e){
              print "The Katalon error is ${e}"
			   echo "[BUILD END]"
			  status = false
		      error("Build failed....")
             }
    } 
 }

        stage('Post Build'){
                logger.info("Email stage...")
				sleep(20)
                step([$class: 'JUnitResultArchiver', testResults: '**/Reports/*/*/*.xml'])				
				print "Test results can be viewed from http://jenkins01:8989job/userManagementDailyVerification/test_results_analyzer/"
                try{
                    logger.info("build completed.")
					if(status){
                  emailext body: "${env.JOB_NAME} was executed successfully", mimeType: 'text/html', subject: "${env.JOB_NAME} build success", to: 'prabhaharan.velu@gsihealth.com,naveen.dhandapani@gsihealth.com' 
}else{
            def jobPath = "${env.JENKINS_HOME}"
            jobPath = getJobPath(jobPath, env.JOB_NAME)
            def consolePath = jobPath+ "\\builds\\${env.BUILD_NUMBER}\\log"
			sleep 5
            echo "reading the console log..."
            def log = readFile(consolePath)
			def buildLog =  lastMatch(log, /\[BUILD START\]([\s|\S]*)\[BUILD END\]/)
            if(buildLog){
                buildLog=replaceAll(buildLog,/\[8mh.+\[Pipeline\](?:(?!\r\n)[\s|\S])*\r\n/,"")
                buildLog=replaceAll(buildLog,/\[8mh.+\[Pipeline\]/,"")				
				}
				def DEFAULT_CONTENT
				echo "${buildLog}"
		   if(buildLog && buildLog.length()!=0){
                DEFAULT_CONTENT="<br>=========CONSOLE LOG=========<br><br><pre>${buildLog}</pre>"
            }else{
			DEFAULT_CONTENT="liquibase execution failed for ${env.JOB_NAME}"
			}	   
		   emailext body: "${DEFAULT_CONTENT}", mimeType: 'text/html', subject: "${env.JOB_NAME} build failure", to: 'continuousdelivery@gsihealth.com,sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com'
}
                }catch(e){
				 echo "[BUILD END]"
                    print e
                    emailext body: "Script has some error! ${e}", subject: "Jenkins warning:${env.JOB_NAME} Build ${currentBuild.displayName}", to: 'continuousdelivery@gsihealth.com'
                }

            }
                    
    }


}
@NonCPS
def getJobPath(rootPath, job_name) {
    def arr = job_name.split("/")
    for (int i = 0; i < arr.length; i++) {
        rootPath += "\\jobs\\${arr[i]}"
    }
    return rootPath
}
//(Checking out[\s|\S]*)stage \(Archive\)
//Don't forget capture group...!
@NonCPS
def String lastMatch(text, regex) {
    def m;
    if ((m = text =~ regex)) {
        return m[0][-1]
    }
    return ''
}

@NonCPS
def String replaceUrl( text, text2) {
    return text.replaceAll("Jenkins01:8989", text2)
}
@NonCPS
def String replaceAll( text, regex, newText) {
    return text.replaceAll(regex, newText)
}