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
	def katalonToolPath=config.katalonToolPath
	def runSoap = config.runSoap
	def testSuitePathLower
	def reportConfigPathPath=config.reportConfigPathPath
	def testSuiteCollectionPath	=config.testSuiteCollectionPath
	def testSuiteReportConfigPath = config.testSuitePath
	def testSuitePath = config.testSuitePath
	def KatalonRep=config.KatalonRep
	def testResults=config.testResults
	
	 print "${KatalonRep}"
 
	 
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
					def checkoutResult = git credentialsId: 'cf7a0040-05b2-4835-b49e-8e5814141f5b', url: "https://sadhasivim@bitbucket.org/GSIHTeam/automationjunitreports.git", changeLog:true
					task.checkoutResult = checkoutResult 
					sleep(10)
					bat 'cd..'
				}
		}
		
		stage('CJunit report generation'){
				step([$class: 'JUnitResultArchiver', testResults: 'AutomationJunitReports\\Reports\\*\\*\\*\\*\\*\\*.xml'])
				step([$class: 'JUnitResultArchiver', testResults: 'AutomationJunitReports\\Reports\\*\\*\\*\\*.xml'])	
		}
		stage('Initialize the report files'){				
				dir("./AutomationJunitReports/Reports"){
				bat 'del /S /q *.*'
				bat 'for /f "usebackq delims=" %%d in (`"dir /ad/b/s | sort /R"`) do rd "%%d"'
				}
				dir("./AutomationJunitReports") {
							bat 'git add -A'
							def gitcmd="git commit -m \"update\""
							bat "${gitcmd}"
							bat 'git push origin HEAD:master'
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