import jenkins.model.Jenkins
import hudson.scm.SubversionChangeLogSet.LogEntry
import com.gsihealth.jenkins.Common
def call(body) {

    // evaluate the body block, and collect configuration into the object
    def config = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = config
	def Patientid = config.Patientid
    body()
node{
 File file = new File("D:\\Sadha\\CCD_Soup\\soapTemplate.xml")
    String var=file.text
    String var1=var.replaceAll("<<CCDpatientID>>","${Patientid}")
    File filenew = new File("D:\\Sadha\\CCD_Soup\\FiftyMegXDSTest-soapui-project.xml")
     filenew << var1
     println var1
     def str="SmartBear\\SoapUI-5.4.0\\bin\\testrunner.bat -sCCDtestSuite -c\"TestCase 1\" D:\\Sadha\\CCD_Soup\\FiftyMegXDSTest-soapui-project.xml"
     println str
     bat "${str}"
     bat 'DEL D:\\Sadha\\CCD_Soup\\FiftyMegXDSTest-soapui-project.xml'
    }
	}
    