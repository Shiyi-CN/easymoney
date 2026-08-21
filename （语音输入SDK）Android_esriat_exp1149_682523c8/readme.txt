
1.使用demo测试时，需将res中除layout外资源拷贝到demo中assets相应的路径下;
2.使用带UI接口时，请将assets下文件拷贝到项目中;
3.文档说明请参考:http://doc.xfyun.cn/msc_android/;
4.在调用sdk时,请将res/layout下xml文件拷贝至工程的layout目录下，此文件为sdk内置ui所需，资源缺失会导致sdk部分功能无法使用;

注： 1. 由于更新优化更新，本次(1138)的libmsc.so库需与本次Msc.jar相匹配，使用之前的Msc.jar包可能会导致出错。


合规接入指南：
为保证用户个人隐私，防止APP不当收集用户信息，我们强烈建议您遵守以下流程接入本SDK保证合规，防止因调用时机不当引发的后果，例如但不限于：APP被应用市场下架等。
1.您需要确保贵APP有《隐私政策》，并且在用户首次启动App时就弹出《隐私政策》争得用户同意。
2.您务必在App的《第三方共享清单及SDK目录》中告知用户MSC SDK收集的个人信息类型以及MSC SDK隐私政策。
    - 个人信息收集说明： MSC SDK需要收集唯一设备识别码（android ID）以提供能力授权服务。
    - 隐私政策：https://www.xfyun.cn/doc/policy/sdk_privacy.html
3.您务必严格遵守如下调用步骤，确保用户同意《隐私政策》之后，且在用户主动使用本SDK提供的各项功能时再进行相关函数调用。
    - 确保App启动后，在用户阅读并同意《隐私政策》并取得用户授权之后，在用户使用SDK功能时，方可调函数SpeechUtility.createUtility(SpeechApp.this, "xxxx")以使用MSC SDK。反之，如果用户不同意《隐私政策》授权，则不允许调用SpeechUtility.createUtility(SpeechApp.this, "xxxx")函数。
    - 参考示例：SDK demo源码中获取到《隐私政策》的用户授权，后续的SDK demo函数SpeechUtility.createUtility(SpeechApp.this, "xxxx")建议在用户使用SDK功能时进行使用。


感谢您使用科大讯飞服务。
官方网站：http://www.xfyun.cn/
