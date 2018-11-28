import com.gsihealth.jenkins.Common
def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
    body()
    def failure = false
    def repo_url = config.repository_url?:"https://subversion.assembla.com/svn/gsihealth.umbrella/${config.svnBranch}/${config.repository}"

    node {

        try{
            stage 'Code checkout'

            //Marks build start
            echo "[BUILD START]"

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
                                            local                : "${config.name}",
                                            remote               : repo_url]],
                    workspaceUpdater    : [$class: 'CheckoutUpdater']])


            stage 'Build'

            //Move to source code directory
            dir("./${config.name}") {

                if (!config.type) {
                    echo "WARNING: The build type is not specified. Using Maven..."
                    config.type="maven"
                }

                def buildEnvVars=[]
                if(config.java==8){
                    buildEnvVars.add("JAVA_HOME=${tool 'jdk8u121'}")
                }else if(config.java&&config.java!=7){
                    error("This Java version is not supported.")
                }

                switch (config.type) {
                    case 'ant':
                        String goal = config.goal?:"clean war"
                        buildEnvVars.add("PATH+ANT=${tool name: 'ANT 1.8.1', type: 'hudson.tasks.Ant$AntInstallation'}/bin")
                        withEnv(buildEnvVars) {
                            bat "ant ${goal}"
                        }
                        break
                    case 'maven':
                        String goal = config.goal?:"clean install"
                        buildEnvVars.addAll(["PATH+MAVEN=${tool name: 'Maven 3.0.4', type: 'hudson.tasks.Maven$MavenInstallation'}/bin",
                                          "MAVEN_OPTS=-XX:MaxPermSize=512m"])
                        withEnv(buildEnvVars) {
                            bat "mvn ${goal} -Dmaven.repo.local=${config.mvnRepo}"
                        }
                        break
                    case 'node':
                        String goal = config.goal?:"app c"
                        bat "npm install"
                        bat "node ${goal}"
                        break

                }

            }

            //TODO: Could be enhanced. Temporary patch for Message app which requires post-build ANT call to rename the war. just for msg app
            if(config.post_build){

                stage 'Post-Build'
                dir("./${config.name}") {
                    withEnv(["PATH+ANT=${tool name: 'ANT 1.8.1', type: 'hudson.tasks.Ant$AntInstallation'}/bin"]) {
                        bat "${config.post_build}"
                    }
                }

            }

            stage 'Archive'
            //Archive artifacts
            switch(config.type){
                case 'ant':
                    archive '*/dist/*.war, */dist/*.zip, **/target/*.war'
                    break
                case 'maven':
                    archive '**/target/*.war, **/target/*.zip, **/target/*.jar'
                    break
                case 'node':
                    archive '**/*.zip'
                    break
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