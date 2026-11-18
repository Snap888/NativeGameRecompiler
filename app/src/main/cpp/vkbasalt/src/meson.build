subdir('shader')
subdir('reshade')

vkBasalt_src = [
    'basalt.cpp',
    'buffer.cpp',
    'command_buffer.cpp',
    'config.cpp',
    'descriptor_set.cpp',
    'effect_cas.cpp',
    'effect.cpp',
    'effect_deband.cpp',
    'effect_dls.cpp',
    'effect_fxaa.cpp',
    'effect_lut.cpp',
    'effect_reshade.cpp',
    'effect_simple.cpp',
    'effect_smaa.cpp',
    'effect_transfer.cpp',
    'fake_swapchain.cpp',
    'format.cpp',
    'framebuffer.cpp',
    'graphics_pipeline.cpp',
    'image.cpp',
    'image_view.cpp',
    'keyboard_input.cpp',
    'logger.cpp',
    'logical_swapchain.cpp',
    'lut_cube.cpp',
    'memory.cpp',
    'renderpass.cpp',
    'reshade_uniforms.cpp',
    'sampler.cpp',
    'shader.cpp',
    'stb_image.c',
    'stb_image_resize.c',
    'util.cpp',
    'vkdispatch.cpp',
]

# build X11-free on Android; toggleKey is unusable there, on/off comes from the mtime watch
vkbasalt_cpp_args = []
vkbasalt_link_with = []
vkbasalt_deps      = [reshade_dep]

if host_machine.system() == 'android'
    vkbasalt_cpp_args += '-DVKBASALT_X11=0'
else
    x11_dep = dependency('x11')
    # X11/X.h library contains many definitions (e.g. None)
    # which conflicts with the rest codebase with with Meson --unity=on option
    keyboard_input_x11_lib = static_library('keyboard_input_x11', 'keyboard_input_x11.cpp')
    vkbasalt_link_with += keyboard_input_x11_lib
    vkbasalt_deps      += x11_dep
endif

conf_paths = configuration_data()
conf_paths.set_quoted('SYSCONFDIR', join_paths(get_option('prefix'), get_option('sysconfdir')))
conf_paths.set_quoted('DATADIR', join_paths(get_option('prefix'), get_option('datadir')))

configure_file(output: 'config_paths.hpp',
               configuration : conf_paths)

shared_library(meson.project_name().to_lower(),
    vkBasalt_src, shader_include,
    cpp_args : vkbasalt_cpp_args,
    link_with: vkbasalt_link_with,
    include_directories : vkBasalt_include_path,
    dependencies : vkbasalt_deps,
    gnu_symbol_visibility: 'hidden',
    install : true,
    install_dir : lib_dir)
