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
    def repo_url = "https://subversion.assembla.com/svn/gsihealthmobileapp/trunk/GSI%20Health"
    node {
GitBuildTask task = new GitBuildTask(env)
        task.setConfig(config)
        task.startDate = new Date()
        try{
           // def path3="RD /S /Q \"D:\\Jenkins\\workspace\\${env.JOB_NAME}\\mobileApps\""
			//bat "${path3}"
            stage 'Code checkout'

            //Marks build start
            echo "[BUILD START]"
            def buildEnvVars=[] 
			//def env=System.getenv()
			//String pythonpath=env["PYTHON_HOME"]
			echo env.PATH
			//Checkout the latest code
            checkout([
                    $class              : 'SubversionSCM',
    //                additionalCredentials: [],
    //                excludedCommitMessages: '',
    //                excludedRegions: '',
    //                excludedRevprop: '',
    //                excludedUsers: '',
                    filterChangelog     : false,
                    ignoreDirPropChanges: false,
    //                includedRegions: '',
                    locations           : [[credentialsId        : '8617c3ba-6b64-444d-9315-517ebd07bfca',
                                            depthOption          : 'infinity',
                                            ignoreExternalsOption: true,
                                            local                : "./app/GSI Health",
                                            remote               : repo_url]],
                    workspaceUpdater    : [$class: 'CheckoutUpdater']])
    sleep 5
			dir("./${config.name}") {
			
                def checkoutResult = git credentialsId: '60c19ca5-84bb-4508-a665-0c7895d3031b', url: "https://sadhasivim@bitbucket.org/sadhasivim/gsi-health.git", changeLog:true
                task.checkoutResult = checkoutResult
			  // bat 'git init'
			  // bat 'git pull https://sadhasivim@bitbucket.org/sadhasivim/gsi-health.git'
                sleep(5)
				//def path1="xcopy /y \"D:\\Jenkins\\workspace\\${env.JOB_NAME}\\mobileApps\"  \"D:\\Jenkins\\workspace\\${env.JOB_NAME}\\app\\GSI Health\" /E"
            dir("./.svn") {
			def path1="xcopy /y \"D:\\Jenkins\\workspace\\${env.JOB_NAME}\\app\\GSI Health\\.svn\" \"D:\\Jenkins\\workspace\\${env.JOB_NAME}\\mobileApps\\.svn\" /E /H"
			bat "${path1}"
			sleep(5)
			}
			bat 'svn upgrade'
			bat 'svn add --force .'
			bat 'svn commit -m "svn git sync" --username sadha.sivim --password Kctech2017'
			
            bat 'cd..'
            }
          //dir("./app/GSI Health") {
		 // bat 'svn cleanup'
		  //bat 'svn commit -m "svn git sync" --username sadha.sivim --password Kctech2017'
		  //}
            stage 'Build'

            //Move to source code directory
            dir("./${config.name}/app") {
                bat 'npm install --prefix app -g titanium'
                bat 'npm install --prefix app -g alloy'
               
            }
			def path="xcopy /y D:\\mobileBuildDependencies\\${config.env}\\config.json  D:\\Jenkins\\workspace\\${env.JOB_NAME}\\mobileApps\\app"
			echo "${path}"
			dir("./${config.name}/app/app"){
			bat "${path}"
			bat 'titanium sdk select 6.1.2.GA'
			def pathtwo="titanium build --platform android -b --android-sdk C:\\Users\\ssivim\\AppData\\Local\\Android\\Sdk\\Sdk --project-dir D:\\Jenkins\\workspace\\${env.JOB_NAME}\\mobileApps"	
		    bat "${pathtwo}"
			}

            
            //Marks build end
            echo "[BUILD END]"

        }catch(e){
            print e
            echo "[BUILD END]"
            failure = true
        }

        if (failure) currentBuild.result = "FAILURE"

        try{

            def jobPath = "${env.JENKINS_HOME}"
            jobPath = getJobPath(jobPath, env.JOB_NAME)
            def consolePath = jobPath+ "\\builds\\${env.BUILD_NUMBER}\\log"

            def revision_path = jobPath+ "\\builds\\${env.BUILD_NUMBER}\\revision.txt"
            echo "reading the svn revision log..."
            def rev_log = readFile(revision_path)

            def BUILD_STATUS = currentBuild.result?:"SUCCESS"
            def SVN_REVISION = lastMatch(rev_log, /\/(\d+)$/)

            if(SVN_REVISION){
                currentBuild.displayName = "#${env.BUILD_NUMBER}_${config.release}.Rev${SVN_REVISION}"
                currentBuild.description = "Revision: ${SVN_REVISION}"
            }

            sleep 5
            echo "reading the console log..."
            def log = readFile(consolePath)

            def to;
            def requested = emailextrecipients([ [$class: 'RequesterRecipientProvider']])
            def culprits = emailextrecipients([[$class: 'CulpritsRecipientProvider']])
            def developers = emailextrecipients([ [$class: 'DevelopersRecipientProvider']])
            def common = new Common();
            def cdteam = common.getCD().join(", ");
            def devteam = common.getDevLead().join(", ")
//            def devteam=""
            println "Started by: ${requested}"
            println "Responsible Developers: ${culprits}"
            println "Developers: ${developers}"
			
			if(failure){

            to = "${cdteam}, ${devteam},"+ emailextrecipients([ [$class: 'RequesterRecipientProvider'],[$class: 'DevelopersRecipientProvider'],[$class: 'CulpritsRecipientProvider']])
            to = replaceAll(to,/, /,",")
            to = replaceAll(to,/ /,",")
            

            print "to: ${to}"
			}
			else{
			to = "${cdteam},"+ emailextrecipients([ [$class: 'RequesterRecipientProvider'],[$class: 'DevelopersRecipientProvider'],[$class: 'CulpritsRecipientProvider']])
            to = replaceAll(to,/, /,",")
            to = replaceAll(to,/ /,",")
            
			print "to: ${to}"
			}			
			
            def buildLog =  lastMatch(log, /\[BUILD START\]([\s|\S]*)\[BUILD END\]/)
            if(buildLog){
                buildLog=replaceAll(buildLog,/\[8mh.+\[Pipeline\](?:(?!\r\n)[\s|\S])*\r\n/,"")
                buildLog=replaceAll(buildLog,/\[8mh.+\[Pipeline\]/,"")

                if(config.type=='ant'){
                    echo "This is ant build. Ensuring the build result from log..."
                    def failedStr = lastMatch(buildLog, /(BUILD FAILED)/)
                    if(failedStr && failedStr.length()!=0){
                        echo "BUILD FAILED string found. Marking the build as failure."
                        currentBuild.result = "FAILURE"
                        BUILD_STATUS = "FAILURE"
                    }
                }

            }

            def IP_URL = replaceUrl(env.BUILD_URL, "10.128.65.100:8989")

            def DEFAULT_SUBJECT = "[${BUILD_STATUS}] - ${env.JOB_NAME} - ${currentBuild.displayName}"
            def headlineColor = BUILD_STATUS=="SUCCESS"?"#26c6da":"#B71C1C"
            def DEFAULT_CONTENT = "<span style='color:${headlineColor};'>${env.JOB_NAME} - ${currentBuild.displayName} - ${BUILD_STATUS}</span> <br><br>"
            if(requested){
                DEFAULT_CONTENT +="This build was started by ${requested}.<br />"
            }
            if(developers){
                DEFAULT_CONTENT +="Developers related to this build: ${developers}.<br />"
            }
            if(culprits){
                DEFAULT_CONTENT +="Developers responsible for this build: ${culprits}.<br />"
            }
            DEFAULT_CONTENT += """<br>
            This build is for SVN Revision ${SVN_REVISION}<br><br>
            Check the build page at <a href="${env.BUILD_URL}">${env.BUILD_URL}</a> or <a href="${IP_URL}">${IP_URL}</a>.<br><br>
            """.stripMargin()
            if(buildLog && buildLog.length()!=0){
                DEFAULT_CONTENT+="<br>=========CONSOLE LOG=========<br><br><pre>${buildLog}</pre>"
            }
            if(!config.disableEmail && to!=null && to.length()!=0){
                emailext body: DEFAULT_CONTENT, subject: DEFAULT_SUBJECT, to: to
            }
        }catch(e){
            emailext body: "Script has some error! ${e}", subject: "Jenkins warning:${env.JOB_NAME} Build ${currentBuild.displayName}", to: 'continuousdelivery@gsihealth.com'
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