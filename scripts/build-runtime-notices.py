"""从实际运行包生成第三方许可清单与随包文本。"""

import argparse
import csv
import hashlib
import io
import json
import shutil
import zipfile
from pathlib import Path, PurePosixPath


REPOSITORY_ROOT = Path(__file__).resolve().parents[1]
AUDIT_PATH = REPOSITORY_ROOT / "docs/open-source/dependency-audit.csv"
LICENSE_TEXT_ROOT = REPOSITORY_ROOT / "docs/open-source/license-texts"


def sha256(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def selected_license(artifact: str, declared: str) -> str:
    """为已记录的多许可构件选择当前分发路径。"""
    if artifact.startswith("logback-"):
        return "EPL-1.0"
    if artifact.startswith("hibernate-core-"):
        return "LGPL-2.1-or-later"
    if artifact.startswith("jakarta.persistence-api-") or artifact.startswith("istack-commons-runtime-"):
        return "EDL-1.0"
    if artifact.startswith(("jna-", "jna-platform-")):
        return "Apache-2.0"
    if artifact.startswith("reactive-streams-"):
        return "MIT-0"
    if artifact.startswith("LatencyUtils-"):
        return "CC0-1.0"
    if artifact.startswith("HdrHistogram-"):
        return "BSD-2-Clause"
    if "Eclipse Public" in declared or "EPL 2.0" in declared:
        return "EPL-2.0"
    if "Eclipse Distribution" in declared or "EDL 1.0" in declared:
        return "EDL-1.0"
    if "BSD-3-Clause" in declared:
        return "BSD-3-Clause"
    if "BSD-2-Clause" in declared:
        return "BSD-2-Clause"
    if "MIT" in declared:
        return "MIT"
    if "Apache" in declared:
        return "Apache-2.0"
    raise ValueError(f"未识别的许可声明：{artifact}: {declared}")


def fallback_text(artifact: str, license_id: str) -> str:
    """优先使用与实际版本对应的上游许可文件。"""
    versioned = {
        "protobuf-java-4.33.1.jar": "protobuf-java-4.33.1.txt",
        "antlr4-runtime-4.13.0.jar": "antlr4-runtime-4.13.0.txt",
        "reactive-streams-1.0.4.jar": "reactive-streams-1.0.4.txt",
    }
    if artifact.startswith("ortools-"):
        return "ortools-9.15.txt"
    if artifact in versioned:
        return versioned[artifact]
    if license_id == "EDL-1.0":
        return "EDL-1.0-from-jaxb-runtime-4.0.5.txt"
    return f"{license_id}.txt"


def copy_embedded_files(archive: zipfile.ZipFile, destination: Path) -> list[str]:
    paths = []
    for entry in archive.infolist():
        member = PurePosixPath(entry.filename)
        if entry.is_dir() or ".." in member.parts or not member.parts or member.parts[0].upper() != "META-INF":
            continue
        is_license_directory = len(member.parts) > 1 and member.parts[1].lower() in ("license", "licenses")
        if not is_license_directory and not any(token in member.name.upper() for token in ("LICENSE", "LICENCE", "NOTICE", "COPYING")):
            continue
        target = destination.joinpath(*member.parts)
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(archive.read(entry))
        paths.append(target)
    return paths


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("runtime_root", type=Path)
    args = parser.parse_args()
    runtime_root = args.runtime_root.resolve()
    backend_jar = runtime_root / "backend/backend.jar"
    simulator_library = runtime_root / "simulator/lib/org.eclipse.paho.mqttv5.client-1.2.5.jar"
    if not backend_jar.is_file() or not simulator_library.is_file():
        raise FileNotFoundError("运行包缺少后端 JAR 或模拟器 Paho JAR")
    license_root = runtime_root / "third-party-licenses"
    source_root = runtime_root / "third-party-sources"
    for output_path in (license_root, source_root, runtime_root / "THIRD_PARTY_LICENSES.csv", runtime_root / "THIRD_PARTY_NOTICES.md"):
        if output_path.exists():
            raise FileExistsError(f"输出已存在，拒绝覆盖：{output_path}")
    shutil.copytree(LICENSE_TEXT_ROOT, license_root / "common")

    with AUDIT_PATH.open(encoding="utf-8-sig", newline="") as handle:
        audit_rows = list(csv.DictReader(handle))
    maven_lookup = {
        f"{row['package'].split(':')[-1]}-{row['version']}.jar": row
        for row in audit_rows if row["ecosystem"] == "Maven"
    }
    npm_lookup = {
        (row["package"], row["version"]): row
        for row in audit_rows if row["ecosystem"] == "npm"
    }
    inventory = []
    fallback_count = 0
    source_count = 0
    with zipfile.ZipFile(backend_jar) as backend:
        embedded_paho_hash = sha256(backend.read("BOOT-INF/lib/org.eclipse.paho.mqttv5.client-1.2.5.jar"))
        libraries = [name for name in backend.namelist() if name.startswith("BOOT-INF/lib/") and name.endswith(".jar")]
        for library_path in libraries:
            filename = PurePosixPath(library_path).name
            row = maven_lookup.get(filename)
            if row is None and filename == "spring-boot-jarmode-tools-3.5.5.jar":
                row = {
                    "package": "org.springframework.boot:spring-boot-jarmode-tools",
                    "version": "3.5.5",
                    "declared_license": "Apache-2.0",
                    "license_metadata_url": "https://repo.maven.apache.org/maven2/org/springframework/boot/spring-boot-jarmode-tools/3.5.5/spring-boot-jarmode-tools-3.5.5.pom",
                }
            if row is None:
                raise ValueError(f"依赖清单未覆盖运行 JAR：{filename}")
            selected = selected_license(filename, row["declared_license"])
            group, artifact = row["package"].split(":")
            maven_directory = Path.home() / ".m2/repository" / group.replace(".", "/") / artifact / row["version"]
            component_directory = license_root / "maven" / filename
            with zipfile.ZipFile(io.BytesIO(backend.read(library_path))) as nested:
                copied = copy_embedded_files(nested, component_directory)
            pom = maven_directory / f"{artifact}-{row['version']}.pom"
            if pom.is_file():
                component_directory.mkdir(parents=True, exist_ok=True)
                shutil.copy2(pom, component_directory / "POM.xml")
            if copied:
                text_paths = [str(path.relative_to(runtime_root)).replace("\\", "/") for path in copied]
                status = "原始 JAR 内许可/NOTICE 已提取"
            else:
                fallback = license_root / "common" / fallback_text(filename, selected)
                if not fallback.is_file():
                    raise FileNotFoundError(f"缺少备用许可正文：{fallback}")
                text_paths = [str(fallback.relative_to(runtime_root)).replace("\\", "/")]
                status = "JAR 未内嵌文本，版权/NOTICE 待逐项复核"
                fallback_count += 1
            source_path = ""
            if selected in ("EPL-1.0", "EPL-2.0", "LGPL-2.1-or-later"):
                source_jar = maven_directory / f"{artifact}-{row['version']}-sources.jar"
                if source_jar.is_file():
                    source_root.mkdir(parents=True, exist_ok=True)
                    target = source_root / source_jar.name
                    shutil.copy2(source_jar, target)
                    source_path = str(target.relative_to(runtime_root)).replace("\\", "/")
                    source_count += 1
                else:
                    status += "；对应源码包缺失"
            inventory.append({
                "ecosystem": "Maven", "component": row["package"], "version": row["version"],
                "artifact": library_path, "declared_license": row["declared_license"],
                "selected_license": selected, "license_texts": "; ".join(text_paths),
                "source_archive": source_path, "metadata_url": row["license_metadata_url"], "status": status,
            })

    if len(inventory) != 90:
        raise ValueError(f"后端 JAR 数量变化，需重新审查：{len(inventory)}")
    if sha256(simulator_library.read_bytes()) != embedded_paho_hash:
        raise ValueError("模拟器 Paho JAR 与已审查的后端内嵌版本不同")

    lock = json.loads((REPOSITORY_ROOT / "frontend/package-lock.json").read_text(encoding="utf-8"))
    npm_count = 0
    npm_fallback_count = 0
    for package_path, lock_data in lock["packages"].items():
        if not package_path.startswith("node_modules/") or lock_data.get("dev"):
            continue
        installed = REPOSITORY_ROOT / "frontend" / package_path
        package = json.loads((installed / "package.json").read_text(encoding="utf-8"))
        package_name, version = package["name"], package["version"]
        alias_name = package_path.split("node_modules/")[-1]
        row = npm_lookup.get((alias_name, version))
        if row is None:
            raise ValueError(f"依赖清单未覆盖前端生产包：{alias_name} => {package_name}@{version}")
        license_id = package.get("license")
        if license_id != row["declared_license"]:
            raise ValueError(f"前端许可元数据不一致：{package_name}@{version}")
        component_directory = license_root / "npm" / (package_path.replace("node_modules/", "").replace("/", "__") + "-" + version)
        component_directory.mkdir(parents=True)
        originals = [item for item in installed.iterdir() if item.is_file() and item.name.upper().startswith(("LICENSE", "LICENCE", "NOTICE", "COPYING"))]
        if originals:
            for item in originals:
                shutil.copy2(item, component_directory / item.name)
            text_paths = [str((component_directory / item.name).relative_to(runtime_root)).replace("\\", "/") for item in originals]
            status = "npm 安装包原始许可文本已复制"
        else:
            upstream = {
                ("element-plus", "2.11.0"): "element-plus-2.11.0.txt",
                ("@vue/devtools-api", "6.6.4"): "vue-devtools-api-6.6.4.txt",
            }.get((package_name, version), "MIT.txt")
            fallback = license_root / "common" / upstream
            text_paths = [str(fallback.relative_to(runtime_root)).replace("\\", "/")]
            status = "同版本上游许可文本已补充" if upstream != "MIT.txt" else "npm 包未附许可文本，版权声明待复核"
            npm_fallback_count += 1
            shutil.copy2(installed / "package.json", component_directory / "package.json")
        inventory.append({
            "ecosystem": "npm", "component": alias_name if alias_name == package_name else f"{alias_name} => {package_name}", "version": version,
            "artifact": "frontend/dist", "declared_license": row["declared_license"],
            "selected_license": license_id, "license_texts": "; ".join(text_paths),
            "source_archive": "", "metadata_url": row["license_metadata_url"], "status": status,
        })
        npm_count += 1
    if npm_count != 65:
        raise ValueError(f"前端生产依赖数量变化，需重新审查：{npm_count}")

    inventory_path = runtime_root / "THIRD_PARTY_LICENSES.csv"
    with inventory_path.open("w", encoding="utf-8-sig", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=list(inventory[0]))
        writer.writeheader()
        writer.writerows(inventory)
    notice = f"""# 第三方许可与分发说明

本清单对应运行包中的后端 JAR（SHA-256：`{sha256(backend_jar.read_bytes())}`）、模拟器 Paho JAR 和前端 `dist`，不代表对 Docker 拉取的镜像层完成审查。本项目自有代码见源码包中的 MIT `LICENSE`；第三方组件仍适用各自许可。

- 后端内嵌 JAR：{len(libraries)} 个；其中 {len(libraries) - fallback_count} 个原包带有许可或 NOTICE 文件，已原样复制到 `third-party-licenses/maven/`。
- 未在 JAR 中发现许可或 NOTICE 文件：{fallback_count} 个。已提供对应许可正文或同版本上游文本与 POM 元数据；具体版权声明和潜在附加 NOTICE 仍须逐项核实。
- 前端生产依赖：{npm_count} 个；其中 {npm_count - npm_fallback_count} 个包自带许可文本，已复制到 `third-party-licenses/npm/`。其余 {npm_fallback_count} 个中，Element Plus 与 Vue Devtools API 使用同版本上游许可文本；`lodash-unified@1.0.3` 只有 MIT 字段，缺少发布包中的具体版权文本。
- 选用 EPL 或 LGPL 路径的运行构件所能取得的对应源码包：{source_count} 个，位于 `third-party-sources/`；Hibernate ORM 6.6.26.Final 的源码 JAR 已包含。源码包保持上游原样，未修改。
- 模拟器单独携带的 Paho JAR 与后端内嵌同版本文件哈希一致，因此在清单中只列一次。

逐项构件、版本、许可声明、正文路径、元数据来源及待复核状态见 `THIRD_PARTY_LICENSES.csv`。`third-party-licenses/common/SOURCES.csv` 记录补充许可正文的原始链接与 SHA-256。保留的上游文件及许可文本不改变其原有授权。本清单是分发证据，不是对所有许可义务已完成的法律结论。

## 尚待人工复核

1. 对 {fallback_count} 个 JAR 中未内嵌文本的构件，继续核对项目级版权与 NOTICE；其中 `redis-authx-core` 的 MIT POM 链接目前无法取得同版本原始版权文本。
2. 核对 OR-Tools 原生库实际打入的 SCIP、HiGHS 等第三方求解器及其版权声明。OR-Tools 9.15 的 Maven POM 对外层构件声明 Apache-2.0，但不能据此替代原生库内部审查。
3. 核对前端最终 bundle 与锁文件生产依赖的实际对应关系，尤其是 `lodash-unified@1.0.3` 的版权声明。
4. 若未来把 PostgreSQL、Redis 或 EMQX 镜像本体一并分发，另行检查镜像层许可；当前 ZIP 只包含 Compose 配置，镜像由 Docker 拉取。

Hibernate 6.6 系列的 LGPL 说明见 https://hibernate.org/orm/releases/6.6/ 与 https://hibernate.org/community/license/ 。许可证文本归档与对应源码的提供方式不替代专业法律复核。
"""
    (runtime_root / "THIRD_PARTY_NOTICES.md").write_text(notice, encoding="utf-8", newline="\r\n")
    print(f"Maven JAR: {len(libraries)}; npm production: {npm_count}; Maven fallback: {fallback_count}; npm fallback: {npm_fallback_count}; source JAR: {source_count}")


if __name__ == "__main__":
    main()
