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
    
    node {
GitBuildTask task = new GitBuildTask(env)
        task.setConfig(config)
        task.startDate = new Date()
        try{
          
            stage('Build'){
            //calling restart groovy
                def PS_deploy = "D:/jenkins_utils/deployment_v2/ps/RestartDashboard.ps1 -env ${config.env} -environmentType ${config.environmentType}"
                bat script: "${getPSCmd(PS_deploy)}"
				}
			
				
        }catch(e){
            print e
            echo "[BUILD END]"
            failure = true
        }
   
   					
        if (failure) {
		echo "sending mail for failure....."
		notifyFailed()
		}else{
		stage('StandardVerification'){
		if(spring){
		 try{
   echo "Starting dailyVerification"
   
			def dailyVer="${config.env}-DailyVerification"
			echo "${dailyVer}"
            build job: "${dailyVer}"
			step([$class: 'Publisher', reportFilenamePattern: '**/testng-results.xml'])
			 }catch(e){
            print e
            echo "There are errors in test"
			step([$class: 'Publisher', reportFilenamePattern: '**/testng-results.xml'])
            //failure = true
        }
		}else{
		echo "skipping standard verification"
		}
       
		}
		
		echo "sending mail....."
		notifyPassed()
		}

       echo "Test results can be views from http://jenkins01:8989/job/${env.JOB_NAME}/test_results_analyzer/"
		
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