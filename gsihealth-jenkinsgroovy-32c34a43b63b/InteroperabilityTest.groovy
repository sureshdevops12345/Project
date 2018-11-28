import com.gsihealth.jenkins.Common
import com.gsihealth.jenkins.runner.GitBuildTask
import com.gsihealth.jenkins.utils.Logger
def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def failure = false
	def liquibaseStatus= false
	def buildEnvVars = []
	GitBuildTask task = new GitBuildTask(env)


    node {

        
		
		
            //Marks build start
            print "[BUILD START]"

            //Checkout the latest code
             dir("./daily-monitoring-interface-scripts") {
			 stage('selenium code checkout'){
                def checkoutResult = git credentialsId: '60c19ca5-84bb-4508-a665-0c7895d3031b', url: "https://sadhasivim@bitbucket.org/GSIHTeam/daily-monitoring-interface-scripts.git", changeLog:true
                task.checkoutResult = checkoutResult
				}
				
				try {
               
                    stage('Script excecution'){
                    if (config.java == 8) {
                        buildEnvVars.add("JAVA_HOME=${tool 'jdk8u121'}")
                    }
                  
							buildEnvVars.add("JAVA_HOME=${tool 'jdk8u121'}")
                            buildEnvVars.addAll(["PATH+MAVEN=${tool name: 'Maven 3.0.4', type: 'hudson.tasks.Maven$MavenInstallation'}/bin",
                                                 "MAVEN_OPTS=-XX:MaxPermSize=512m"])        						 
						   withEnv(buildEnvVars) {
                             print "*****RUNNING THE TESTCASES*****"
                            String goal = config.goal?:"clean install -e"
                            def mvnTestCmd="mvn ${goal}"	
					        print "${mvnTestCmd}"
                            bat "${mvnTestCmd}"                 						                         						
							liquibaseStatus = true
							}
			  }
                
            }catch (e) {
			   liquibaseStatus = false
			   echo "[BUILD END]"
			   print "Script execution failed"
			   print e
			}
          }  
        
		 if(liquibaseStatus){
		 def fileJobName = new File("D:/Sadha/test.txt")
         fileJobName.text = "${env.JOB_NAME}"
		     print "***** SCRIPT EXECUTED SUCCESSFULLY *****"
			 step([$class: 'JUnitResultArchiver', testResults: '**/daily-monitoring-interface-scripts/target/surefire-reports/junitreports/*.xml'])
	 try{
             echo "Starting log extraction"
   
			def dailyVer="Interoperability-GetServerLogsNM"
			echo "${dailyVer}"
            build job: "${dailyVer}",parameters: [[$class: 'StringParameterValue', name: 'resourceJob', value: "${env.JOB_NAME}" ]]
			step([$class: 'Publisher', reportFilenamePattern: '**/testng-results.xml'])
			 }catch(e){
            print e
            echo "There are errors in test"
			step([$class: 'Publisher', reportFilenamePattern: '**/testng-results.xml'])
            //failure = true
        }			 
		 }else{
		 //def fileJobName = new File("D://Sadha//test.txt")
         //fileJobName.write = "${env.JOB_NAME}"
            print "***** SCRIPT EXECUTION FAILED *****"
			//bat'cd D:\\Sadha'
			bat'NUL>D:\\Sadha\\jobName.txt'
			bat "echo ${env.JOB_NAME}>D:\\Sadha\\jobName.txt"
				 try{
             echo "Starting log extraction"
   
			def dailyVer="Interoperability-GetServerLogsNM"
			echo "${dailyVer}"
            build job: "${dailyVer}"
			step([$class: 'Publisher', reportFilenamePattern: '**/testng-results.xml'])
			 }catch(e){
            print e
            echo "There are errors in test"
			step([$class: 'Publisher', reportFilenamePattern: '**/testng-results.xml'])
            //failure = true
        }
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
		   if(buildLog && buildLog.length()!=0){
                DEFAULT_CONTENT="<br>=========CONSOLE LOG=========<br><br><pre>${buildLog}</pre>"
            }else{
			step([$class: 'JUnitResultArchiver', testResults: '**/daily-monitoring-interface-scripts/target/surefire-reports/junitreports/*.xml'])	
			DEFAULT_CONTENT="script execution failed for ${env.JOB_NAME}"
			}
			
   		   
		   emailext body: "${DEFAULT_CONTENT}", mimeType: 'text/html', subject: "${env.JOB_NAME} build failure", to: 'continuousdelivery@gsihealth.com,sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com'
		   error("Build failed....")
		}
	} 
		
   }



@NonCPS
def notifyFailed(body) {
def bodyContent=body
mail (to: 'sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com,prabhaharan.velu@gsihealth.com',
subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) has failed....",
body: "Job Failed ${bodyContent}.");

}
@NonCPS
def notifyPassed() {
mail (to: 'sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com,prabhaharan.velu@gsihealth.com',
//mail (to: 'ContinuousDelivery@gsihealth.com',
 subject: "Job '${env.JOB_NAME}' (${env.BUILD_NUMBER}) has passed....",
 body: "Job passed ${env.BUILD_URL}.");
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
