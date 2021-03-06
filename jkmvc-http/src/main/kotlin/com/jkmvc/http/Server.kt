package com.jkmvc.http

import com.jkmvc.common.Config
import com.jkmvc.common.ucFirst
import javax.servlet.http.HttpServletRequest
import javax.servlet.http.HttpServletResponse
import kotlin.reflect.KFunction

/**
 * 服务端对象，用于处理请求
 *
 * @author shijianhang
 * @date 2016-10-6 上午9:27:56
 *
 */
object Server:IServer {

    /**
     * http配置
     */
    public val config = Config.instance("http", "yaml")

    /**
     * 是否调试
     */
    public val debug:Boolean = config.getBoolean("debug", false)!!;

    /**
     * 处理请求
     *
     * @param req
     * @param res
     * @return 是否处理，如果没有处理（如静态文件请求），则交给下一个filter/默认servlet来处理
     */
    public override fun run(request: HttpServletRequest, response: HttpServletResponse):Boolean{
        //　构建请求对象
        val req:Request = Request(request);
        if(debug){
            // 路由
            httpLogger.debug("请求uri: ${req.method} ${req.routeUri}")

            // curl命令
            if(!req.isUpload()) // 上传请求，要等到设置了上传子目录，才能访问请求参数
                httpLogger.debug(req.buildCurlCommand())
        }

        //　如果是静态文件请求，则跳过路由解析
        if(req.isStaticFile())
            return false;

        // 构建响应对象
        val res:Response = Response(response);

        try{
            // 解析路由
            if (!req.parseRoute())
                throw RouteException("当前uri没有匹配路由：" + req.requestURI);

            // 调用路由对应的controller与action
            callController(req, res);
            return true;
        }
        /* catch (e：RouteException)
        {
            // 输出404响应
            res.setStatus(404).send();
        }  */
        catch (e: Exception) {
//            res.render("异常 - " + e.message)
            e.printStackTrace(res.prepareWriter())
            httpLogger.debug("处理uri[${req.routeUri}]出错", e)
            return true
        }

    }

    /**
     * 调用controller与action
     *
     * @param req
     * @param res
     */
    private fun callController(req: Request, res: Response) {
        // 获得controller类
        val clazz:ControllerClass? = ControllerLoader.getControllerClass(req.controller);
        if (clazz == null)
            throw RouteException ("Controller类不存在：" + req.controller);

        // 获得action方法
        val action: KFunction<*>? = clazz.getActionMethod(req.action);
        if (action == null){
            val method = "action" + req.action.ucFirst()
            throw RouteException ("控制器${req.controller}不存在方法：${method}()");
        }

        // 创建controller
        val controller:Controller = clazz.constructer.call() as Controller;

        // 允许跨域
        if(config.getBoolean("allowCrossDomain", false)!!){
            res.setHeader("Access-Control-Allow-Origin", "*");
            res.setHeader("Access-Control-Allow-Methods", "POST,GET,OPTIONS,PUT,DELETE,HEAD");
            res.setHeader("Access-Control-Allow-Headers", "origin,cache-control,content-type,accept,hash-referer,x-requested-with,token");// 跨域验证登录用户，要用到请求头中的token
            if(req.isOptions())
                return;
        }

        // 设置req/res属性
        controller.req = req;
        controller.res = res;

        // 调用controller的action方法
        controller.executeAction(action)
    }
}
