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
             dir("./Liquibase") {
			 stage('Liquibase code checkout'){
                def checkoutResult = git credentialsId: '60c19ca5-84bb-4508-a665-0c7895d3031b', url: "https://sadhasivim@bitbucket.org/GSIHTeam/Liquibase", changeLog:true
                task.checkoutResult = checkoutResult
				}
				
				try {
               
                    stage('Liquibase excecution'){
                    if (config.java == 8) {
                        buildEnvVars.add("JAVA_HOME=${tool 'jdk8u121'}")
                    }
                  
							buildEnvVars.add("JAVA_HOME=${tool 'jdk8u121'}")
                            buildEnvVars.addAll(["PATH+MAVEN=${tool name: 'Maven 3.0.4', type: 'hudson.tasks.Maven$MavenInstallation'}/bin",
                                                 "MAVEN_OPTS=-XX:MaxPermSize=512m"])
												 
												
												 
												 
                          
						 
						   withEnv(buildEnvVars) {
                                                 def tagCmd="mvn liquibase:tag -Dliquibase.tag=\"${env.BUILD_NUMBER}\""
												 def update="mvn clean install exec:java -Dspecification.operation=\"update\" -Dspecification.buildNo=\"${env.BUILD_NUMBER}\""	
												 def updateCmd="mvn liquibase:update"	
												
                            print "${tagCmd}"
							print "${update}"
						    print "${updateCmd}"
							
                            //bat 'mvn clean install'							
                            
                            bat "${update}"	
                            	dir("D:\\Jenkins\\workspace\\liquibaseJob\\MySQL Utilities 1.6") {
                                //def sb = new StringBuilder()
						def rollbackScriptsPath="D:\\Jenkins\\workspace\\liquibaseJob\\liquibase\\scripts\\rollbackscripts\\drop${env.BUILD_NUMBER}.txt"
		                 File theInfoFiles = new File("${rollbackScriptsPath}")
                         if( !theInfoFiles.exists() ) {
                           println "File does not exist"
                         } else {
                               def lines = theInfoFiles.readLines()
				for (String line : lines) {
                println(line)
                def str = line.split('\\.');
                for(def ele:str){
                print "${ele}"
                }
                
                //def compareCMD="mysqldbcompare --server1=liquibaseuser:Test123#@10.153.0.205 --server2=liquibaseuser:Test123#@10.153.0.205 ${schema}:${schema}copy --run-all-tests --changes-for=server1 --difftype=sql > ${env.BUILD_NUMBER}${schema}.txt 2>&1"                      
                def dumpCMD="D:\\temp\\mysqldump.exe -u liquibaseuser -h 10.153.0.205 -pTest123#  ${str[0]} ${str[1]} >> D:\\Jenkins\\workspace\\liquibaseJob\\liquibase\\scripts\\Rollback\\drop${env.BUILD_NUMBER}.sql"                      
              bat "${dumpCMD}"
               }
               }	 
               }

                              
                            bat "${tagCmd}"							
							bat "${updateCmd}"
							
                         						
							liquibaseStatus = true
							}
							print "${env.SQL_HOME}"
						dir("D:\\Jenkins\\workspace\\liquibaseJob\\MySQL Utilities 1.6") {
						//def myList = []
						def sb = new StringBuilder()
						
		                 File theInfoFile = new File('D:\\Jenkins\\workspace\\liquibaseJob\\schemaList.txt')
                         if( !theInfoFile.exists() ) {
                           println "File does not exist"
                         } else {
						 def schemas = theInfoFile.readLines()
				for (String schema : schemas) {
				 try{
                        println(schema)
						def compareCMD="mysqldbcompare --server1=liquibaseuser:Test123#@10.153.0.205 --server2=liquibaseuser:Test123#@10.153.0.205 ${schema}:${schema}copy --run-all-tests --changes-for=server1 --difftype=sql > ${env.BUILD_NUMBER}${schema}.txt 2>&1"                      
					    bat "${compareCMD}"
								 }catch (e) {
			 File theInfoFile1 = new File("D:\\Jenkins\\workspace\\liquibaseJob\\MySQL Utilities 1.6\\${env.BUILD_NUMBER}${schema}.txt")
					    def fileContent =  theInfoFile1.text
						sb.append(fileContent)
						sb.append("\n")   
			   print "liquibase comparison failed"
			   print e
			}
					                     
						}
						
						def fileName = "${env.BUILD_NUMBER}_compare.txt"
                        def inputFile = new File("D:\\Jenkins\\workspace\\liquibaseJob\\MySQL Utilities 1.6\\"+fileName)
						def s = sb
                        if(inputFile.exists())
                          {

                              print "A file named " + fileName + " already exisits in the same folder"
                              inputFile.write("${s}")
                           }else{
                          inputFile.write("${s}")


                                 }
                         }	
						 //def compareCMD="mysqldbcompare --server1=liquibaseuser:Test123#@10.153.0.205 --server2=liquibaseuser:Test123#@10.153.0.205 connect:connectcopy --run-all-tests --changes-for=server1 --difftype=sql > ${env.BUILD_NUMBER}_compare.txt 2>&1"
                         //print "${compareCMD}"
					     //bat "${compareCMD}"
						 print "liquibase execution "
				
					   }
					  def generateRollback="mvn exec:java -Dspecification.operation=\"rollbackGenerate\" -Dspecification.sourceFile=\"D:\\Jenkins\\workspace\\liquibaseJob\\MySQL Utilities 1.6\\${env.BUILD_NUMBER}_compare.txt\" -Dspecification.destinationFile=\"D:\\Jenkins\\workspace\\liquibaseJob\\Liquibase\\scripts\\Rollback\\${env.BUILD_NUMBER}_RollbackScript.sql\""
                      bat "${generateRollback}"
			  }
                
            }catch (e) {
			   liquibaseStatus = false
			   echo "[BUILD END]"
			   print "liquibase execution failed"
			   print e
			}
          }  
        
		 if(liquibaseStatus){
		     try{
		
            stage('Code checkout'){
            //Marks build start
            echo "[BUILD START]"
			
  dir("./Dashboard_Liquibase") {
            //Checkout the latest code
		def checkoutResult = git credentialsId: '60c19ca5-84bb-4508-a665-0c7895d3031b', url: "https://sadhasivim@bitbucket.org/GSIHTeam/dashboard_liquibase.git", changeLog:true
              task.checkoutResult = checkoutResult
			   }
        }
            stage('Build'){

            //Move to source code directory
            dir("./Dashboard_Liquibase") {

                if (!config.type) {
                    echo "WARNING: The build type is not specified. Using Maven..."
                    config.type="maven"
                }

               
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
}
            //TODO: Could be enhanced. Temporary patch for Message app which requires post-build ANT call to rename the war. just for msg app
            if(config.post_build){
                 stage('Post-Build'){
                dir("./${config.name}") {
                    withEnv(["PATH+ANT=${tool name: 'ANT 1.8.1', type: 'hudson.tasks.Ant$AntInstallation'}/bin"]) {
                        bat "${config.post_build}"
                    }
                }
				}

            }
			
          stage('Archive'){
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
			dir("./Liquibase"){
         def successScript="mvn exec:java -Dspecification.operation=\"success\""
		 print "${successScript}"
          bat "${successScript}"
		  }
            //Marks build end
            echo "[BUILD END]"
			
			}
			
		  }
		     catch(e){
            print e
            echo "[BUILD END]"
			bat 'cd..'
			dir("./Liquibase"){
             withEnv(buildEnvVars) { 
                 print "rollingback db changes due to build failure"
				            def failureScript="mvn exec:java -Dspecification.operation=\"rollback\""
                           def rollBackCmd="mvn liquibase:rollback -Dliquibase.rollbackTag=\"${env.BUILD_NUMBER}\""	
					        print "${rollBackCmd}"
							print "${failureScript}"
                            bat "${rollBackCmd}"
							bat "${failureScript}"
             }
			 }
            failure = true
        }}else{
            print "SKIPPING BUILD EXECUTION DUE TO FAILURE OF DB SCRIPT"
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
			DEFAULT_CONTENT="liquibase execution failed for ${env.JOB_NAME}"
			}
			
   		   
		   emailext body: "${DEFAULT_CONTENT}", mimeType: 'text/html', subject: "${env.JOB_NAME} build failure", to: 'continuousdelivery@gsihealth.com,sadha.sivim@gsihealth.com,Murugesan.Nambi@gsihealth.com'
          dir("./Liquibase") {
		  def failureScripts="mvn exec:java -Dspecification.operation=\"rollback\""
		  bat "${failureScripts}"
		  bat 'git add -A'
		 def gitcmd="git commit -m \"update\""
		  bat "${gitcmd}"
		  bat 'git push origin HEAD:master'
		  }
		   error("Build failed....")
		}
        
	
if(liquibaseStatus){
        dir("./Liquibase") {
		bat 'git add -A'
		 def gitcmd="git commit -m \"update\""
		bat "${gitcmd}"
		  bat 'git push origin HEAD:master'
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
//          def devteam=""
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