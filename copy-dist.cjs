const fs = require('fs');
const path = require('path');

const srcDir = path.join(__dirname, 'app', 'build', 'dist', 'wasmJs', 'productionExecutable');
const destDir = path.join(__dirname, 'dist');

function copyFolderRecursiveSync(source, target) {
    if (!fs.existsSync(target)) {
        fs.mkdirSync(target, { recursive: true });
    }

    if (fs.lstatSync(source).isDirectory()) {
        const files = fs.readdirSync(source);
        files.forEach((file) => {
            const curSource = path.join(source, file);
            const curTarget = path.join(target, file);
            if (fs.lstatSync(curSource).isDirectory()) {
                copyFolderRecursiveSync(curSource, curTarget);
            } else {
                fs.copyFileSync(curSource, curTarget);
                console.log(`Copied: ${curSource} -> ${curTarget}`);
            }
        });
    }
}

console.log('Cleaning destination directory: ' + destDir);
if (fs.existsSync(destDir)) {
    fs.rmSync(destDir, { recursive: true, force: true });
}
fs.mkdirSync(destDir, { recursive: true });

if (fs.existsSync(srcDir)) {
    console.log('Copying from ' + srcDir + ' to ' + destDir);
    copyFolderRecursiveSync(srcDir, destDir);
    console.log('Successfully copied WasmJs production files to dist!');
} else {
    console.error('Source directory ' + srcDir + ' does not exist. Did you run gradle build?');
    process.exit(1);
}
