#!/home/rarora/careoregon/careoregon/bin/python
import os
import ftplib
from ftplib import FTP
import logging as LOG
import datetime
import glob
 
datadiffRoot = '/home/rarora/careoregon'
 
host = 'ftp-gsihealth.egnyte.com'
usr = 'techservices$gsihealth'
pwd = 'myPasswd'
ftpRoot = '/Private/techservices/careoregon'
 
egnyteHost = 'ftp-gsihealth.egnyte.com'
egnyteUsr = 'techservices$gsihealth'
egnytePwd = 'myPasswd'
egnyteftpRoot = '/Private/techservices/careoregon/output'
 
todaysDate = datetime.date.today().strftime("%Y-%m-%d")
indexKey = 'CM MRN'
 
outDir = os.path.join(datadiffRoot, 'out')
logDir = os.path.join(datadiffRoot, 'logs')
dataDir = os.path.join(datadiffRoot, 'data')
archDir = os.path.join(datadiffRoot, 'arch')
 
ftpFile = 'co-' + todaysDate + '.csv'
ftpPath = os.path.join(ftpRoot, ftpFile)
localFile = os.path.join(dataDir, ftpFile)
outFile = 'co-' + todaysDate + '.json'
outPath = os.path.join(outDir, outFile)
currFile = max(glob.glob(os.path.join(dataDir, 'co-*.csv')), key=os.path.getctime)
LOG.basicConfig(filename=os.path.join(logDir, todaysDate + '.log'),
                level=LOG.INFO,
                format='%(asctime)s %(message)s')
ftp = FTP(host)
[ftp://ftp.login(usr]ftp.login(usr, pwd)
LOG.info('Logged into the ' + host + " ftp server")
LOG.info('File to be downloaded [' + ftpPath + ']')
lf = open(localFile, "wb")
 
LOG.info('Download [' + localFile + '] started')
[ftp://ftp.retrbinary("RETR]ftp.retrbinary("RETR " + ftpPath, lf.write, 8 * 1024)
[ftp://ftp.quit()]ftp.quit()
lf.close()
LOG.info('Download [' + localFile + '] completed successfully')
 
LOG.info('Diff for [' + currFile + '] and [' + localFile + '] started')
cmd='csvdiff -o {0} \"{1}\" {2} {3}'.format(outPath, indexKey, localFile, currFile)
LOG.info('Executing [ {} ] '.format(cmd))
os.system(cmd)
LOG.info('Finished executing [ {} ] '.format(cmd))
 
LOG.info('Logging into egnyte FTP')
session = ftplib.FTP(egnyteHost, egnyteUsr, egnytePwd)
file = open(outPath,'rb')
LOG.info('Storing ' + outFile + ' in egnyte ftp')
session.storbinary('STOR ' + os.path.join(egnyteftpRoot, outFile) , file)
file.close()
session.quit()
LOG.info('Finished uploading diff file [ {} ] to egnyte FTP'.format( os.path.join(egnyteftpRoot, outFile)))
