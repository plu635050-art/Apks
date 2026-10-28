/* Real ARM64 guest mount-move diagnostic. Uses ONLY Linux syscalls. */
typedef unsigned long U;
static long sc(long nr,long a,long b,long c,long d,long e,long f){ register long x0 __asm__("x0")=a,x1 __asm__("x1")=b,x2 __asm__("x2")=c,x3 __asm__("x3")=d,x4 __asm__("x4")=e,x5 __asm__("x5")=f,x8 __asm__("x8")=nr;__asm__ volatile("svc #0":"+r"(x0):"r"(x1),"r"(x2),"r"(x3),"r"(x4),"r"(x5),"r"(x8):"memory");return x0;}
static int length(const char*s){int n=0;while(s[n])n++;return n;}
static void writeall(const char*s){sc(64,1,(long)s,length(s),0,0,0);}
static void digit(long n){char b[32];int k=0; if(n<0){writeall("-");n=-n;}do{b[k++]='0'+(n%10);n/=10;}while(n);char o[33];for(int j=0;j<k;j++)o[j]=b[k-1-j];o[k]=0;writeall(o);}
static void op(const char*name,long result){writeall("PRIMEVM18 ");writeall(name);writeall(" result=");digit(result);writeall("\n");}
static long mt(const char*s,const char*t,const char*fs,long flags,const char*data){return sc(40,(long)s,(long)t,(long)fs,flags,(long)data,0);}
static long md(const char*s){return sc(34,-100,(long)s,0755,0,0,0);}
static void showfile(const char*name){char b[512];long fd=sc(56,-100,(long)name,0,0,0,0);if(fd<0){op("open-error",fd);return;}long n=sc(63,fd,(long)b,511,0,0,0);if(n>0){b[n]=0;writeall("PRIMEVM18 FILE ");writeall(name);writeall(": ");writeall(b);}sc(57,fd,0,0,0,0,0);}
__attribute__((used)) static void start(void){
writeall("PRIMEVM18_START\n");
op("mount_proc",mt("proc","/proc","proc",0,0));
op("mount_sys",mt("sysfs","/sys","sysfs",0,0));
op("mount_dev",mt("tmpfs","/dev","tmpfs",2,"mode=0755"));
showfile("/sys/block/vda/dev");
long fd=sc(56,-100,(long)"/sys/block/vda/dev",0,0,0,0);char b[80];long n=fd>=0?sc(63,fd,(long)b,79,0,0,0):-1; if(fd>=0)sc(57,fd,0,0,0,0,0);
long major=0,minor=0;if(n>0){int i=0;while(i<n&&b[i]>='0'&&b[i]<='9'){major=major*10+b[i++]-'0';}if(i<n&&b[i]==':')i++;while(i<n&&b[i]>='0'&&b[i]<='9'){minor=minor*10+b[i++]-'0';}}
op("mkdir_dev_block",md("/dev/block"));
unsigned long dev=((minor&0xff)|((major&0xfff)<<8)|((minor&~0xff)<<12)|((major&~0xfff)<<32));
op("mknodat_dev_block_vda",sc(33,-100,(long)"/dev/block/vda",0060600,dev,0,0));
op("mount_real_system_ext4",mt("/dev/block/vda","/system","ext4",1,"barrier=1"));
op("mkdir_system_dev",md("/system/dev"));
op("MOVE_DEV_TO_SYSTEM_DEV",mt("/dev","/system/dev",0,8192,0));
op("MOVE_DEV_BACK",mt("/system/dev","/dev",0,8192,0));
writeall("PRIMEVM18_END\n");
sc(93,0,0,0,0,0,0);
}
__attribute__((naked)) void _start(void){__asm__ volatile("bl start\nmov x0, #0\nmov x8, #93\nsvc #0\n");}