package com.dormitory.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.dormitory.annotation.PermInfo;
import com.dormitory.constant.PermType;
import com.dormitory.entity.SysPerm;
import com.dormitory.service.SysPermService;
import com.dormitory.vo.Json;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.MethodUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.stereotype.Controller;
import org.springframework.util.ClassUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.lang.reflect.Method;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @ClassName SysPermController
 * @Description TODO
 * @Author CoderL
 * @Date 2020/3/3 17:05
 **/
@PermInfo(value = "系统权限模块",pval = "a:sys:perm")
@RequiresPermissions("a:sys:perm")
@RestController
@RequestMapping("/sys_perm")
public class SysPermController {

    private static final Logger log = LoggerFactory.getLogger(SysPermController.class);

    @Resource
    private SysPermService permService;

    @RequestMapping("/list/all")
    public Json listAllPermission() {
        String oper = "list menu,button,api permissions";
        QueryWrapper<SysPerm> params = new QueryWrapper<>();
        params.in("ptype",PermType.MENU, PermType.BUTTON, PermType.API);
        List<SysPerm> list = permService.list(params);
        if (list.isEmpty()){
            return Json.succ(oper);
        }else{
            Map<Integer, List<SysPerm>> permMap = list.stream().collect(Collectors.groupingBy(SysPerm::getPtype));
            List<SysPerm> buttonPermList = permMap.get(PermType.BUTTON);
            Map<String, List<SysPerm>> buttonsGroupedByParent = new HashMap<>();
            if (buttonPermList!=null&&!buttonPermList.isEmpty()){
                buttonsGroupedByParent = buttonPermList.stream().collect(Collectors.groupingBy(SysPerm::getParent));
            }
            return Json.succ(oper, "permMap", permMap).data("btnPermMap", buttonsGroupedByParent);
        }
    }

    /**
     * 按钮权限列表
     * @return
     */
    @RequestMapping("/list/btn_perm_map")
    public Json listButtonPermMapGroupByParent() {
        String oper = "list btn perm map group by parent";
        QueryWrapper<SysPerm> params = new QueryWrapper<>();
        params.eq("ptype", PermType.BUTTON);
        List<SysPerm> buttonPermList = permService.list(params);

        Map<String, List<SysPerm>> buttonsGroupedByParent = new HashMap<>();
        if (buttonPermList!=null&&!buttonPermList.isEmpty()){
            buttonsGroupedByParent = buttonPermList.stream().collect(Collectors.groupingBy(SysPerm::getParent));
        }
        return Json.succ(oper, "btnPermMap", buttonsGroupedByParent);
    }

    /**
     * 同步菜单
     * @param body
     * @return
     */
    @PostMapping("/sync/menu")
    public Json syncMenuPermission(@RequestBody String body) {
        String oper = "sync menu permission";
        log.info("{}, body: {}", oper, body);
        List<SysPerm> notSyncedPerms = JSON.parseArray(body, SysPerm.class);
        if (!notSyncedPerms.isEmpty()){
            permService.remove(new QueryWrapper<SysPerm>().eq("ptype",PermType.MENU));
            permService.saveOrUpdate(notSyncedPerms);
        }
        return Json.succ(oper);
    }

    /**
     * 同步接口
     * @param body
     * @return
     */
    @PostMapping("/sync/api")
    public Json syncApiPermission(@RequestBody String body) {
        String oper = "sync api permission";
        log.info("{}, body: {}", oper, body);
        List<SysPerm> notSyncedPerms = JSON.parseArray(body, SysPerm.class);
        if (!notSyncedPerms.isEmpty()){
            permService.remove(new QueryWrapper<SysPerm>().eq("ptype",PermType.API));
            permService.saveOrUpdate(notSyncedPerms);
        }
        return Json.succ(oper);
    }

    @PostMapping
    public Json add(@RequestBody String body) {

        String oper = "add permission";
        SysPerm perm = JSON.parseObject(body, SysPerm.class);

        if (StringUtils.isEmpty(perm.getPval())) {
            return Json.fail(oper, "权限值不能为空");
        }

        QueryWrapper<SysPerm> params = new QueryWrapper<>();
        params.eq("pval", perm.getPval());
        params.select("pname","pval");
        SysPerm permDB = permService.getOne(params);

        if (permDB != null) {
            return Json.fail(oper, "权限值已存在：" + permDB.getPname() + "（" + perm.getPval() + "）");
        }

        //保存
        perm.setCreated(new Date());
        boolean success = permService.save(perm);
        return Json.result(oper, success)
                .data("created", perm.getCreated());
    }

    @DeleteMapping
    public Json delete(@RequestBody String body) {
        String oper = "delete permission";
        log.info("{}, body: {}", oper, body);
        JSONObject jsonObj = JSON.parseObject(body);
        String pval = jsonObj.getString("pval");
        if (StringUtils.isBlank(pval)) {
            return Json.fail(oper, "无法删除权限：参数为空（权限值）");
        }
        boolean success = permService.removeById(pval);
        return Json.result(oper, success);
    }

    @PatchMapping("/info")
    public Json update(@RequestBody String body) {

        String oper = "update permission";
        log.info("{}, body: {}", oper, body);

        SysPerm perm = JSON.parseObject(body, SysPerm.class);
        if (StringUtils.isBlank(perm.getPval())) {
            return Json.fail(oper, "无法更新权限：参数为空（权限值）");
        }

        SysPerm updateData = new SysPerm();
        updateData.setPval(perm.getPval());
        updateData.setPname(perm.getPname());
        updateData.setUpdated(new Date());
        boolean success = permService.updateById(updateData);
        return Json.result(oper, success).data("updated", perm.getUpdated());
    }

    @Autowired
    private ApplicationContext context;

    @RequestMapping("/meta/api")
    public Json listApiPermMetadata() {
        String oper = "list api permission metadata";
        log.info(oper);
        final String basicPackage = ClassUtils.getPackageName(this.getClass());
        Map<String, Object> map = context.getBeansWithAnnotation(Controller.class);
        Collection<Object> beans = map.values();
        List<SysPerm> apiList = beans.stream().filter(b -> StringUtils.equals(basicPackage, ClassUtils.getPackageName(b.getClass())))
                .map(bean -> {
                    Class<?> clz = bean.getClass();
                    SysPerm moduleApiPerm = getModulePerm(clz);
                    List<SysPerm> methodApiPerm = getApiPerm(clz, moduleApiPerm.getPval());
                    moduleApiPerm.getChildren().addAll(methodApiPerm);
                    return moduleApiPerm;
                }).collect(Collectors.toList());
        return Json.succ(oper,"apiList",apiList);
    }

    /**
     * 获取控制器上的方法上的注释，生成后台接口权限的信息
     *
     * @param clz
     * @return
     */
    private List<SysPerm> getApiPerm(Class<?> clz,final String parentPval) {
        //获取clz类上有RequiresPermissions注解的所有方法
        List<Method> apiMethods = MethodUtils.getMethodsListWithAnnotation(clz.getSuperclass(), RequiresPermissions.class);
        return apiMethods.stream().map(method -> {
            //pname首选
            //获取method方法上的PermInfo注解的元数据
            PermInfo piAnno = AnnotationUtils.getAnnotation(method, PermInfo.class);
            String pnamePrimary = piAnno!=null?piAnno.value():null;
            //pname备选
            String pnameSub = method.getName();
            //pval值
            //获取method方法上的RequiresPermissions注解的元数据
            RequiresPermissions rpAnno = AnnotationUtils.getAnnotation(method, RequiresPermissions.class);
            SysPerm perm = new SysPerm();
            if (StringUtils.isNotBlank(pnamePrimary)){
                perm.setPname(pnamePrimary);
            }else{
                perm.setPname(pnameSub);
            }
            perm.setParent(parentPval);
            perm.setPtype(PermType.API);
            assert rpAnno != null;
            perm.setPval(rpAnno.value()[0]);
            return perm;
        }).collect(Collectors.toList());
    }

    /**
     * 获取控制器上的注释，生成后台接口模块权限的信息
     *
     * @param clz
     * @return
     */
    public SysPerm getModulePerm(Class<?> clz) {
        SysPerm perm = new SysPerm();
        //首选值
        PermInfo piAnno = AnnotationUtils.getAnnotation(clz, PermInfo.class);
        if (piAnno == null) {
            //由于使用了RequiresPermissions注解的类在运行时会使用动态代理，即clz在运行时是一个动态代理，所以需要getSuperClass获取实际的类型
            piAnno = AnnotationUtils.getAnnotation(clz.getSuperclass(), PermInfo.class);
        }
        String pnamePrimary = null;
        String pvalPrimary = null;
        String pvalPrimary2 = null;
        if (piAnno != null) {
            piAnno.value();
            pnamePrimary = piAnno.value();
            pvalPrimary = piAnno.pval();
        }



        //赋值
        if (StringUtils.isNotBlank(pnamePrimary)) {
            perm.setPname(pnamePrimary);
        }
        perm.setPtype(PermType.API);
        return perm;
    }


}
