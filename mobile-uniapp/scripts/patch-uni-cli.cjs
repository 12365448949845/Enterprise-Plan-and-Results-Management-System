const fs = require('node:fs')
const path = require('node:path')

const target = path.resolve(
  __dirname,
  '../node_modules/@dcloudio/uni-cli-shared/dist/vue/transforms/index.js',
)

if (!fs.existsSync(target)) {
  process.exit(0)
}

const source = fs.readFileSync(target, 'utf8')
const broken = 'uni_shared_1.H5_BUILT_IN_TAG_NAMES.reduce'
const fixed = '(uni_shared_1.H5_BUILT_IN_TAG_NAMES || uni_shared_1.BUILT_IN_TAG_NAMES).reduce'

if (source.includes(fixed)) {
  process.exit(0)
}

if (!source.includes(broken)) {
  console.warn('[patch-uni-cli] expected source pattern not found, skip')
  process.exit(0)
}

fs.writeFileSync(target, source.replace(broken, fixed))
console.log('[patch-uni-cli] patched @dcloudio/uni-cli-shared H5 built-in tag constant')
